package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.dao.mapper.RepoAnalysisFindingMapper;
import com.socialflow.dao.mapper.RepoAnalysisMapper;
import com.socialflow.dao.mapper.RepoBookmarkMapper;
import com.socialflow.model.dto.AnalyzeRepoDTO;
import com.socialflow.model.dto.FindingStatusDTO;
import com.socialflow.model.dto.SaveBookmarkDTO;
import com.socialflow.model.entity.RepoAnalysis;
import com.socialflow.model.entity.RepoAnalysisFinding;
import com.socialflow.model.entity.RepoBookmark;
import com.socialflow.model.vo.AnalysisStatsVO;
import com.socialflow.model.vo.CodeAnalysisVO;
import com.socialflow.model.vo.CodeFindingVO;
import com.socialflow.model.vo.LanguageStatVO;
import com.socialflow.model.vo.RepoBookmarkVO;
import com.socialflow.model.vo.RepoCommitVO;
import com.socialflow.service.codeanalysis.CodeAnalysisService;
import com.socialflow.service.codeanalysis.GitRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    private final GitRepoService gitRepoService;
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

    private RepoAnalysis initRecord(Long userId, AnalyzeRepoDTO dto, String type) {
        RepoAnalysis a = new RepoAnalysis();
        a.setUserId(userId);
        a.setGitUrl(dto.getGitUrl());
        a.setAnalysisType(type);
        a.setStatus("PENDING");
        a.setStage("INIT");
        a.setProgressPercent(0);
        a.setProgressMessage("已提交，等待调度");
        a.setHighCount(0);
        a.setMediumCount(0);
        a.setLowCount(0);
        a.setIsFavorite(0);
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

    @Override
    public CodeAnalysisVO getByShareToken(String shareToken) {
        RepoAnalysis a = analysisMapper.selectOne(new LambdaQueryWrapper<RepoAnalysis>()
                .eq(RepoAnalysis::getShareToken, shareToken)
                .last("limit 1"));
        if (a == null) throw new BusinessException("分享链接无效");
        return toVo(a, true);
    }

    @Override
    public String generateShareToken(Long userId, Long analysisId) {
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("分析记录不存在");
        }
        if (a.getShareToken() != null) return a.getShareToken();
        String token = UUID.randomUUID().toString().replace("-", "");
        RepoAnalysis upd = new RepoAnalysis();
        upd.setId(analysisId);
        upd.setShareToken(token);
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

    @Override
    public void updateFindingStatus(Long userId, Long findingId, FindingStatusDTO dto) {
        RepoAnalysisFinding f = findingMapper.selectById(findingId);
        if (f == null) throw new BusinessException("finding 不存在");
        RepoAnalysis a = analysisMapper.selectById(f.getAnalysisId());
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("无权修改该 finding");
        }
        RepoAnalysisFinding upd = new RepoAnalysisFinding();
        upd.setId(findingId);
        upd.setStatus(dto.getStatus());
        upd.setResolutionNote(dto.getResolutionNote());
        findingMapper.updateById(upd);
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
        List<RepoAnalysis> all = analysisMapper.selectList(new LambdaQueryWrapper<RepoAnalysis>()
                .eq(RepoAnalysis::getUserId, userId)
                .orderByDesc(RepoAnalysis::getCreateTime)
                .last("limit 500"));

        AnalysisStatsVO vo = new AnalysisStatsVO();
        vo.setTotalCount(all.size());

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        int monthly = 0;
        int highTotal = 0, medTotal = 0, lowTotal = 0;
        long scoreSum = 0;
        int scoreCount = 0;
        Map<String, Integer> repoHit = new LinkedHashMap<>();
        Map<String, Integer> repoLastScore = new HashMap<>();
        Map<java.time.LocalDate, Integer> dayHit = new TreeMap<>();

        for (RepoAnalysis a : all) {
            if (a.getCreateTime() != null && a.getCreateTime().isAfter(monthStart)) monthly++;
            if (a.getHighCount() != null) highTotal += a.getHighCount();
            if (a.getMediumCount() != null) medTotal += a.getMediumCount();
            if (a.getLowCount() != null) lowTotal += a.getLowCount();
            if (a.getOverallScore() != null) {
                scoreSum += a.getOverallScore();
                scoreCount++;
            }
            if (a.getGitUrl() != null) {
                repoHit.merge(a.getGitUrl(), 1, Integer::sum);
                repoLastScore.putIfAbsent(a.getGitUrl(), a.getOverallScore());
            }
            if (a.getCreateTime() != null) {
                dayHit.merge(a.getCreateTime().toLocalDate(), 1, Integer::sum);
            }
        }

        vo.setMonthlyCount(monthly);
        vo.setHighTotal(highTotal);
        vo.setMediumTotal(medTotal);
        vo.setLowTotal(lowTotal);
        vo.setTotalHighRisk(highTotal);
        vo.setAverageScore(scoreCount > 0 ? (double) scoreSum / scoreCount : null);

        // 近 30 天趋势
        java.time.LocalDate today = java.time.LocalDate.now();
        List<AnalysisStatsVO.DailyPoint> trend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            AnalysisStatsVO.DailyPoint p = new AnalysisStatsVO.DailyPoint();
            p.setDate(d);
            p.setCount(dayHit.getOrDefault(d, 0));
            trend.add(p);
        }
        vo.setDailyTrend(trend);

        // 已解决 finding 数量
        Long resolved = findingMapper.selectCount(new LambdaQueryWrapper<RepoAnalysisFinding>()
                .eq(RepoAnalysisFinding::getStatus, "RESOLVED"));
        vo.setResolvedCount(resolved == null ? 0 : resolved.intValue());

        // Top 5 仓库
        List<AnalysisStatsVO.RepoHot> tops = new ArrayList<>();
        repoHit.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> {
                    AnalysisStatsVO.RepoHot h = new AnalysisStatsVO.RepoHot();
                    h.setGitUrl(e.getKey());
                    h.setAnalyzeCount(e.getValue());
                    h.setLastScore(repoLastScore.get(e.getKey()));
                    tops.add(h);
                });
        vo.setTopRepos(tops);

        // 分类分布：按 finding category 聚合
        List<RepoAnalysisFinding> allFindings = all.isEmpty() ? List.of() :
                findingMapper.selectList(new LambdaQueryWrapper<RepoAnalysisFinding>()
                        .in(RepoAnalysisFinding::getAnalysisId,
                            all.stream().map(RepoAnalysis::getId).toList()));
        Map<String, Integer> catHit = new HashMap<>();
        for (RepoAnalysisFinding f : allFindings) {
            if (f.getCategory() != null) catHit.merge(f.getCategory(), 1, Integer::sum);
        }
        List<AnalysisStatsVO.CategoryStat> cats = new ArrayList<>();
        catHit.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    AnalysisStatsVO.CategoryStat c = new AnalysisStatsVO.CategoryStat();
                    c.setCategory(e.getKey());
                    c.setCount(e.getValue());
                    cats.add(c);
                });
        vo.setCategoryStats(cats);

        return vo;
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
            return List.of();
        }
    }
}
