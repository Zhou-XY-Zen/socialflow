package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.dao.mapper.LlmCallLogMapper;
import com.socialflow.dao.mapper.RepoAnalysisDashboardMapper;
import com.socialflow.dao.mapper.RepoAnalysisFindingMapper;
import com.socialflow.dao.mapper.RepoAnalysisMapper;
import com.socialflow.dao.mapper.RepoBookmarkMapper;
import com.socialflow.model.dto.AnalyzeRepoDTO;
import com.socialflow.model.dto.FindingStatusDTO;
import com.socialflow.model.dto.SaveBookmarkDTO;
import com.socialflow.model.entity.LlmCallLog;
import com.socialflow.model.entity.RepoAnalysis;
import com.socialflow.model.entity.RepoAnalysisFinding;
import com.socialflow.model.entity.RepoBookmark;
import com.socialflow.model.vo.AnalysisStatsVO;
import com.socialflow.model.vo.CodeAnalysisVO;
import com.socialflow.model.vo.CodeFindingVO;
import com.socialflow.model.vo.LanguageStatVO;
import com.socialflow.model.vo.LlmCallLogVO;
import com.socialflow.model.vo.RepoBookmarkVO;
import com.socialflow.model.vo.RepoCommitVO;
import com.socialflow.service.codeanalysis.CodeAnalysisService;
import com.socialflow.service.codeanalysis.FindingFeedbackService;
import com.socialflow.service.codeanalysis.GitRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 代码分析门面实现 —— 负责：
 *   1. 同步入口：triggerXxx 立即 INSERT 一条 PENDING 记录，委托给
 *      {@link CodeAnalysisAsyncRunner} 真正异步执行，立即返回 analysisId。
 *   2. 查询：getAnalysis / getByShareToken / history / dashboardStats。
 *   3. 辅助：收藏 / 分享 token / finding 状态标注 / commit 列表。
 *
 * 【为什么把 @Async 拆到独立 bean？】
 *   Spring 的 @Async 基于 AOP 代理实现。同类内部方法调用（this.foo()）不走代理，
 *   导致 @Async 失效、在 HTTP 线程同步执行、前端 60s 超时。
 *   {@link CodeAnalysisAsyncRunner} 作为独立 @Component 注入到这里，以"外部 bean
 *   调用"的方式触发，Spring 代理才会拦截并切换到异步线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAnalysisServiceImpl implements CodeAnalysisService {

    private final RepoAnalysisMapper analysisMapper;
    private final RepoAnalysisFindingMapper findingMapper;
    private final RepoBookmarkMapper bookmarkMapper;
    private final LlmCallLogMapper llmCallLogMapper;
    private final RepoAnalysisDashboardMapper dashboardMapper;
    private final GitRepoService gitRepoService;
    private final FindingFeedbackService findingFeedbackService;
    /** 异步执行器（独立 bean，让 @Async 代理生效） */
    private final CodeAnalysisAsyncRunner asyncRunner;
    /** Git 凭证服务（用户私有仓库认证） */
    private final com.socialflow.service.codeanalysis.CredentialService credentialService;
    /** commit 列表默认条数 */
    private static final int DEFAULT_COMMIT_LIMIT = 50;

    // ================== trigger ==================

    @Override
    public Long triggerProjectOverview(Long userId, AnalyzeRepoDTO dto) {
        RepoAnalysis a = initRecord(userId, dto, "PROJECT_OVERVIEW");
        a.setBranch(dto.getBranch());
        analysisMapper.insert(a);
        asyncRunner.runProjectOverview(userId, a.getId(), dto);
        return a.getId();
    }

    @Override
    public Long triggerCommitReview(Long userId, AnalyzeRepoDTO dto) {
        if (dto.getCommitSha() == null || dto.getCommitSha().isBlank()) {
            throw new BusinessException("提交审查必须指定 commitSha");
        }
        RepoAnalysis a = initRecord(userId, dto, "COMMIT_REVIEW");
        a.setCommitSha(dto.getCommitSha());
        a.setBranch(dto.getBranch());
        analysisMapper.insert(a);
        asyncRunner.runCommitReview(userId, a.getId(), dto);
        return a.getId();
    }

    @Override
    public Long triggerDiffReview(Long userId, AnalyzeRepoDTO dto) {
        if (dto.getBaseRef() == null || dto.getHeadRef() == null) {
            throw new BusinessException("对比分析必须提供 baseRef + headRef");
        }
        RepoAnalysis a = initRecord(userId, dto, "DIFF_REVIEW");
        a.setBaseRef(dto.getBaseRef());
        a.setHeadRef(dto.getHeadRef());
        analysisMapper.insert(a);
        asyncRunner.runDiffReview(userId, a.getId(), dto);
        return a.getId();
    }

    /** 用户诉求落库长度上限（与 prompt 软截断对齐） */
    private static final int USER_REQ_MAX_LEN = 8000;

    private RepoAnalysis initRecord(Long userId, AnalyzeRepoDTO dto, String type) {
        RepoAnalysis a = new RepoAnalysis();
        a.setUserId(userId);
        a.setGitUrl(dto.getGitUrl());
        a.setAnalysisType(type);
        a.setStatus(RepoAnalysis.STATUS_PENDING);
        a.setStage("INIT");
        a.setProgressPercent(0);
        a.setProgressMessage("已提交，等待调度");
        a.setHighCount(0);
        a.setMediumCount(0);
        a.setLowCount(0);
        a.setIsFavorite(0);
        // 保存用户诉求，便于结果页回显 + AsyncRunner 注入 prompt
        String req = dto.getUserRequirements();
        if (req != null && req.length() > USER_REQ_MAX_LEN) {
            req = req.substring(0, USER_REQ_MAX_LEN);
            log.warn("[CodeAnalysis] userRequirements 长度超限已截断到 {} 字符", USER_REQ_MAX_LEN);
        }
        a.setUserRequirements(req);
        return a;
    }

    // ================== 查询 ==================

    @Override
    public CodeAnalysisVO getAnalysis(Long userId, Long analysisId) {
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || (a.getIsDeleted() != null && a.getIsDeleted() == 1)) {
            throw new BusinessException("分析记录不存在");
        }
        if (userId != null && !userId.equals(a.getUserId())) {
            throw new BusinessException("无权访问该分析记录");
        }
        return toVo(a, true);
    }

    /** 分享 token 默认有效期（天）；业务上一周足够大部分协作场景。可在 application.yml 覆盖。 */
    private static final int SHARE_TOKEN_TTL_DAYS = 7;
    /** 分享链接累计访问次数上限，超过后 token 失效（防爬虫/暴力枚举） */
    private static final int SHARE_TOKEN_MAX_ACCESS = 10_000;

    @Override
    public CodeAnalysisVO getByShareToken(String shareToken) {
        RepoAnalysis a = analysisMapper.selectOne(new LambdaQueryWrapper<RepoAnalysis>()
                .eq(RepoAnalysis::getShareToken, shareToken)
                .last("limit 1"));
        if (a == null) throw new BusinessException("分享链接无效");
        // 过期校验
        if (a.getShareExpireAt() != null && a.getShareExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("分享链接已过期，请联系作者重新生成");
        }
        // 访问次数上限
        int count = a.getShareAccessCount() == null ? 0 : a.getShareAccessCount();
        if (count >= SHARE_TOKEN_MAX_ACCESS) {
            throw new BusinessException("分享链接访问次数已达上限，请联系作者重新生成");
        }
        // 递增计数（弱一致，无需强同步）
        RepoAnalysis upd = new RepoAnalysis();
        upd.setId(a.getId());
        upd.setShareAccessCount(count + 1);
        analysisMapper.updateById(upd);
        return toVo(a, true);
    }

    @Override
    public String generateShareToken(Long userId, Long analysisId) {
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("分析记录不存在");
        }
        // 若已存在且未过期，复用旧 token 并延长有效期；否则生成新 token 并重置计数
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newExpire = now.plusDays(SHARE_TOKEN_TTL_DAYS);
        String token = a.getShareToken();
        boolean expired = a.getShareExpireAt() == null || a.getShareExpireAt().isBefore(now);
        if (token == null || expired) {
            token = UUID.randomUUID().toString().replace("-", "");
        }
        RepoAnalysis upd = new RepoAnalysis();
        upd.setId(analysisId);
        upd.setShareToken(token);
        upd.setShareExpireAt(newExpire);
        if (expired) upd.setShareAccessCount(0);
        analysisMapper.updateById(upd);
        return token;
    }

    @Override
    public Page<CodeAnalysisVO> history(Long userId, Integer current, Integer size,
                                        String analysisType, String keyword) {
        Page<RepoAnalysis> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<RepoAnalysis> w = new LambdaQueryWrapper<RepoAnalysis>()
                .eq(RepoAnalysis::getUserId, userId)
                .orderByDesc(RepoAnalysis::getCreateTime);
        if (analysisType != null && !analysisType.isBlank()) {
            w.eq(RepoAnalysis::getAnalysisType, analysisType);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like(RepoAnalysis::getGitUrl, keyword);
        }
        Page<RepoAnalysis> res = analysisMapper.selectPage(page, w);
        Page<CodeAnalysisVO> voPage = new Page<>(res.getCurrent(), res.getSize(), res.getTotal());
        voPage.setRecords(res.getRecords().stream().map(a -> toVo(a, false)).toList());
        return voPage;
    }

    @Override
    public void delete(Long userId, Long analysisId) {
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || !a.getUserId().equals(userId)) return;
        analysisMapper.deleteById(analysisId);
    }

    @Override
    public void toggleFavorite(Long userId, Long analysisId) {
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("分析记录不存在");
        }
        RepoAnalysis upd = new RepoAnalysis();
        upd.setId(analysisId);
        upd.setIsFavorite(Integer.valueOf(1).equals(a.getIsFavorite()) ? 0 : 1);
        analysisMapper.updateById(upd);
    }

    /** 合法的目标状态 */
    private static final java.util.Set<String> FINDING_STATUSES = java.util.Set.of(
            RepoAnalysisFinding.STATUS_UNRESOLVED,
            RepoAnalysisFinding.STATUS_RESOLVED,
            RepoAnalysisFinding.STATUS_IGNORED);

    /** 合法的关闭原因（status != UNRESOLVED 时必填） */
    private static final java.util.Set<String> FINDING_DISMISS_REASONS = java.util.Set.of(
            RepoAnalysisFinding.REASON_INVALID,
            RepoAnalysisFinding.REASON_ALREADY_FIXED,
            RepoAnalysisFinding.REASON_NOT_APPLICABLE,
            RepoAnalysisFinding.REASON_OTHER);

    @Override
    public void updateFindingStatus(Long userId, Long findingId, FindingStatusDTO dto) {
        RepoAnalysisFinding f = findingMapper.selectById(findingId);
        if (f == null) throw new BusinessException("finding 不存在");
        RepoAnalysis a = analysisMapper.selectById(f.getAnalysisId());
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("无权修改该 finding");
        }

        // === 状态机校验 ===
        // 1) 目标状态必须合法
        String target = dto.getStatus();
        if (target == null || !FINDING_STATUSES.contains(target)) {
            throw new BusinessException("finding 状态不合法，只允许 UNRESOLVED/RESOLVED/IGNORED");
        }
        // 2) dismissedReason 与 status 的一致性
        String reason = dto.getDismissedReason();
        if (RepoAnalysisFinding.STATUS_UNRESOLVED.equals(target)) {
            // 回退为未解决 → 必须清空关闭原因，避免脏数据
            reason = null;
        } else {
            // 关闭/忽略 → 必须给出合法关闭原因
            if (reason == null || !FINDING_DISMISS_REASONS.contains(reason)) {
                throw new BusinessException(
                        "关闭原因必填且必须是 INVALID/ALREADY_FIXED/NOT_APPLICABLE/OTHER");
            }
        }

        // MyBatis-Plus 默认不把 null 字段写库；dismissed_reason 需要清空时，
        // 必须用 UpdateWrapper.set(field, null) 强制写 null。
        if (reason == null) {
            findingMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RepoAnalysisFinding>()
                            .eq(RepoAnalysisFinding::getId, findingId)
                            .set(RepoAnalysisFinding::getStatus, target)
                            .set(RepoAnalysisFinding::getResolutionNote, dto.getResolutionNote())
                            .set(RepoAnalysisFinding::getDismissedReason, null));
        } else {
            RepoAnalysisFinding upd = new RepoAnalysisFinding();
            upd.setId(findingId);
            upd.setStatus(target);
            upd.setResolutionNote(dto.getResolutionNote());
            upd.setDismissedReason(reason);
            findingMapper.updateById(upd);
        }
        // INVALID 反馈触发屏蔽列表刷新（异步刷新避免阻塞 UI）
        if (RepoAnalysisFinding.REASON_INVALID.equals(reason)) {
            try {
                findingFeedbackService.refresh();
            } catch (Exception e) {
                log.warn("[Wave8] 屏蔽列表刷新失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<RepoCommitVO> listCommits(Long userId, String gitUrl, String branch, Integer limit) {
        File repo = null;
        try {
            int n = limit == null ? DEFAULT_COMMIT_LIMIT : limit;
            var cred = userId == null ? java.util.Optional.<com.socialflow.model.entity.RepoAuthCredential>empty()
                    : credentialService.resolveForUrl(userId, gitUrl);
            repo = gitRepoService.shallowClone(gitUrl, branch, n, cred.orElse(null));
            return gitRepoService.listCommits(repo, n);
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    @Override
    public AnalysisStatsVO dashboardStats(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        java.time.LocalDate today = java.time.LocalDate.now();
        LocalDateTime trendStart = today.minusDays(29).atStartOfDay();

        AnalysisStatsVO vo = new AnalysisStatsVO();

        // 1) 一次 SQL 汇总：总数/月度数/月度成功数/平均分/高中低累计
        Map<String, Object> s = dashboardMapper.summary(userId, monthStart);
        vo.setTotalCount(asInt(s.get("total_count")));
        vo.setMonthlyCount(asInt(s.get("monthly_count")));
        long monthlySuccess = asLong(s.get("monthly_success"));
        int highTotal = asInt(s.get("high_total"));
        int medTotal  = asInt(s.get("medium_total"));
        int lowTotal  = asInt(s.get("low_total"));
        Object avg = s.get("avg_score");
        vo.setAverageScore(avg == null ? null : ((Number) avg).doubleValue());
        vo.setHighTotal(highTotal);
        vo.setMediumTotal(medTotal);
        vo.setLowTotal(lowTotal);
        vo.setTotalHighRisk(highTotal);

        // 2) 近 30 天趋势：SQL group by day，内存侧补齐没数据的日期
        List<Map<String, Object>> rows = dashboardMapper.dailyTrend(userId, trendStart);
        Map<java.time.LocalDate, Integer> dayHit = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object d = r.get("day");
            java.time.LocalDate ld = (d instanceof java.sql.Date sd) ? sd.toLocalDate()
                    : (d instanceof java.time.LocalDate l) ? l
                    : java.time.LocalDate.parse(String.valueOf(d));
            dayHit.put(ld, asInt(r.get("cnt")));
        }
        List<AnalysisStatsVO.DailyPoint> trend = new ArrayList<>(30);
        for (int i = 29; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            AnalysisStatsVO.DailyPoint p = new AnalysisStatsVO.DailyPoint();
            p.setDate(d);
            p.setCount(dayHit.getOrDefault(d, 0));
            trend.add(p);
        }
        vo.setDailyTrend(trend);

        // 3) 已解决 finding：SQL 直接 COUNT
        vo.setResolvedCount((int) dashboardMapper.resolvedFindingCount(userId));

        // 4) Top 5 仓库：SQL 聚合 + 子查询取最新评分
        List<AnalysisStatsVO.RepoHot> tops = new ArrayList<>();
        for (Map<String, Object> r : dashboardMapper.topRepos(userId, 5)) {
            AnalysisStatsVO.RepoHot h = new AnalysisStatsVO.RepoHot();
            h.setGitUrl(String.valueOf(r.get("gitUrl")));
            h.setAnalyzeCount(asInt(r.get("analyzeCount")));
            Object ls = r.get("lastScore");
            h.setLastScore(ls == null ? null : ((Number) ls).intValue());
            tops.add(h);
        }
        vo.setTopRepos(tops);

        // 5) Finding 分类分布：SQL GROUP BY category
        List<AnalysisStatsVO.CategoryStat> cats = new ArrayList<>();
        for (Map<String, Object> r : dashboardMapper.categoryStats(userId)) {
            AnalysisStatsVO.CategoryStat c = new AnalysisStatsVO.CategoryStat();
            c.setCategory(String.valueOf(r.get("category")));
            c.setCount(asInt(r.get("cnt")));
            cats.add(c);
        }
        vo.setCategoryStats(cats);

        // 6) LLM Token 本月聚合：SQL SUM 避免拉回 N 条日志在内存算
        Map<String, Object> tok = dashboardMapper.monthlyTokenSummary(userId, monthStart);
        long tokensMonthly = asLong(tok.get("total_tokens"));
        vo.setTokensMonthly(tokensMonthly);
        vo.setTokensMonthlyPrompt(asLong(tok.get("prompt_tokens")));
        vo.setTokensMonthlyCompletion(asLong(tok.get("completion_tokens")));
        vo.setLlmCallsMonthly(asInt(tok.get("call_count")));
        vo.setTokensPerAnalysisAvg(monthlySuccess > 0 ? (int) (tokensMonthly / monthlySuccess) : 0);

        // 7) Wave 8 反馈闭环指标
        try {
            long invalidCount = findingFeedbackService.countInvalid();
            long ignoredCount = findingFeedbackService.countIgnored();
            long totalFindings = findingFeedbackService.countTotalFindings();
            vo.setFeedbackInvalidCount(invalidCount);
            vo.setFeedbackIgnoredCount(ignoredCount);
            vo.setFalsePositiveRate(totalFindings > 0
                    ? Math.round(invalidCount * 1000.0 / totalFindings) / 10.0
                    : 0.0);
            vo.setDismissedRulesCount(findingFeedbackService.getDismissedRuleRefs().size());
            List<AnalysisStatsVO.RuleInvalidStat> topInvalid = new ArrayList<>();
            for (var item : findingFeedbackService.topInvalid(5)) {
                AnalysisStatsVO.RuleInvalidStat si = new AnalysisStatsVO.RuleInvalidStat();
                si.setRuleRef(item.ruleRef());
                si.setCount(item.count());
                topInvalid.add(si);
            }
            vo.setTopInvalidRules(topInvalid);
        } catch (Exception e) {
            log.warn("[Dashboard] Wave 8 反馈指标聚合失败", e);
        }
        return vo;
    }

    /** MyBatis Map 结果取整型，兼容 BigDecimal / Long / Integer */
    private static int asInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private static long asLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    @Override
    public List<LlmCallLogVO> listLlmCalls(Long userId, Long analysisId) {
        // 校验归属
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("分析记录不存在");
        }
        List<LlmCallLog> logs = llmCallLogMapper.selectList(
                new LambdaQueryWrapper<LlmCallLog>()
                        .eq(LlmCallLog::getAnalysisId, analysisId)
                        .orderByAsc(LlmCallLog::getCreateTime));
        return logs.stream().map(l -> {
            LlmCallLogVO v = new LlmCallLogVO();
            BeanUtils.copyProperties(l, v);
            return v;
        }).toList();
    }

    // ================== 书签 ==================

    @Override
    public List<RepoBookmarkVO> listBookmarks(Long userId) {
        List<RepoBookmark> list = bookmarkMapper.selectList(
                new LambdaQueryWrapper<RepoBookmark>()
                        .eq(RepoBookmark::getUserId, userId)
                        .orderByDesc(RepoBookmark::getCreateTime));
        return list.stream().map(b -> {
            RepoBookmarkVO v = new RepoBookmarkVO();
            BeanUtils.copyProperties(b, v);
            return v;
        }).toList();
    }

    @Override
    public RepoBookmarkVO saveBookmark(Long userId, SaveBookmarkDTO dto) {
        RepoBookmark b;
        if (dto.getId() != null) {
            b = bookmarkMapper.selectById(dto.getId());
            if (b == null || !b.getUserId().equals(userId)) throw new BusinessException("书签不存在");
            b.setNickname(dto.getNickname());
            b.setGitUrl(dto.getGitUrl());
            b.setBranch(dto.getBranch());
            b.setTags(dto.getTags());
            bookmarkMapper.updateById(b);
        } else {
            b = new RepoBookmark();
            b.setUserId(userId);
            b.setNickname(dto.getNickname());
            b.setGitUrl(dto.getGitUrl());
            b.setBranch(dto.getBranch() != null ? dto.getBranch() : "main");
            b.setTags(dto.getTags());
            bookmarkMapper.insert(b);
        }
        RepoBookmarkVO v = new RepoBookmarkVO();
        BeanUtils.copyProperties(b, v);
        return v;
    }

    @Override
    public void deleteBookmark(Long userId, Long bookmarkId) {
        RepoBookmark b = bookmarkMapper.selectById(bookmarkId);
        if (b == null || !b.getUserId().equals(userId)) return;
        bookmarkMapper.deleteById(bookmarkId);
    }

    // ================== 工具方法（查询侧） ==================

    private CodeAnalysisVO toVo(RepoAnalysis a, boolean loadFindings) {
        CodeAnalysisVO v = new CodeAnalysisVO();
        BeanUtils.copyProperties(a, v);
        v.setTechStack(readJsonList(a.getTechStackJson(), String.class));
        v.setLanguageStats(readJsonList(a.getLanguageStatsJson(), LanguageStatVO.class));
        if (loadFindings && !"PROJECT_OVERVIEW".equals(a.getAnalysisType())) {
            List<RepoAnalysisFinding> fs = findingMapper.selectList(
                    new LambdaQueryWrapper<RepoAnalysisFinding>()
                            .eq(RepoAnalysisFinding::getAnalysisId, a.getId())
                            .orderByAsc(RepoAnalysisFinding::getLevel));
            List<CodeFindingVO> fvs = fs.stream().map(f -> {
                CodeFindingVO fv = new CodeFindingVO();
                BeanUtils.copyProperties(f, fv);
                return fv;
            }).toList();
            v.setFindings(fvs);
        }
        return v;
    }

    private <T> List<T> readJsonList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json,
                    JsonUtil.mapper().getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            // 黄山版 2.2.2：catch 后记日志
            log.warn("[CodeAnalysis] readJsonList 失败, clazz={}, jsonPrefix={}",
                    clazz.getSimpleName(), json.substring(0, Math.min(80, json.length())), e);
            return List.of();
        }
    }
}
