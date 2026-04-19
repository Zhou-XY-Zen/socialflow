package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialflow.common.enums.LlmProvider;
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
import com.socialflow.model.vo.*;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.codeanalysis.CodeAnalysisService;
import com.socialflow.service.codeanalysis.CodeReviewPrompts;
import com.socialflow.service.codeanalysis.GitRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 代码分析门面实现 —— 把 Git 操作 + LLM 调用 + 持久化串起来。
 *
 * 每个 trigger 方法：
 *   1. 立即 INSERT 一条 PENDING 记录并返回 id（同步）
 *   2. @Async 启动真正分析任务：克隆 → 读上下文 → LLM → 解析 → UPDATE
 *   3. 失败时把 status 置 FAILED + errorMsg
 *
 * 轮询走 {@link #getAnalysis(Long, Long)} 即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAnalysisServiceImpl implements CodeAnalysisService {

    private final RepoAnalysisMapper analysisMapper;
    private final RepoAnalysisFindingMapper findingMapper;
    private final RepoBookmarkMapper bookmarkMapper;
    private final GitRepoService gitRepoService;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    @Value("${socialflow.code-analysis.provider:DEEPSEEK}")
    private String defaultProviderCode;

    @Value("${socialflow.code-analysis.model:deepseek-chat}")
    private String model;

    @Value("${socialflow.code-analysis.temperature:0.3}")
    private Double temperature;

    /** diff 最大字节数（~40KB → ~10K tokens） */
    private static final int MAX_DIFF_BYTES = 40 * 1024;

    /** 关键文件总字节上限（~30KB） */
    private static final int MAX_KEY_FILES_BYTES = 30 * 1024;

    /** 目录树层数 */
    private static final int TREE_DEPTH = 3;

    /** commit 列表默认条数 */
    private static final int DEFAULT_COMMIT_LIMIT = 50;

    // ================== trigger ==================

    @Override
    public Long triggerProjectOverview(Long userId, AnalyzeRepoDTO dto) {
        RepoAnalysis a = initRecord(userId, dto, "PROJECT_OVERVIEW");
        a.setBranch(dto.getBranch());
        analysisMapper.insert(a);
        runProjectOverview(a.getId(), dto);
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
        runCommitReview(a.getId(), dto);
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
        runDiffReview(a.getId(), dto);
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

    // ================== 异步任务主体 ==================

    @Async
    protected void runProjectOverview(Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 10, "克隆仓库中...");
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(),
                    dto.getCloneDepth() != null ? dto.getCloneDepth() : 1);

            updateProgress(analysisId, "SCANNING", 30, "扫描语言与目录结构...");
            Map<String, Long> langs = gitRepoService.scanLanguageStats(repo, dto.getExcludeDirs());
            String tree = gitRepoService.buildTreeView(repo, TREE_DEPTH, dto.getExcludeDirs());
            String keyFiles = gitRepoService.readKeyFiles(repo, MAX_KEY_FILES_BYTES);

            updateProgress(analysisId, "ANALYZING", 55, "调用 LLM 生成项目概览...");
            String repoName = extractRepoName(dto.getGitUrl());
            String langStatsText = formatLangStats(langs);
            String prompt = CodeReviewPrompts.projectOverviewUser(repoName, tree, langStatsText, keyFiles);

            LlmResponse resp = callLlm(CodeReviewPrompts.projectOverviewSystem(), prompt);

            updateProgress(analysisId, "RENDERING", 85, "解析结果并入库...");
            JsonNode root = parseJson(resp.getContent());

            RepoAnalysis update = new RepoAnalysis();
            update.setId(analysisId);
            update.setStatus("SUCCESS");
            update.setStage("DONE");
            update.setProgressPercent(100);
            update.setProgressMessage("分析完成");
            update.setSummaryMd(getText(root, "summaryMd"));
            update.setMermaidCode(getText(root, "mermaidCode"));
            update.setTechStackJson(writeJson(root.get("techStack")));
            update.setLanguageStatsJson(writeJson(computeLangStatsVO(langs)));
            update.setDurationMs(System.currentTimeMillis() - start);
            update.setLlmTokensUsed(safeTotal(resp));
            analysisMapper.updateById(update);

            log.info("[CodeAnalysis] projectOverview OK id={} duration={}ms",
                    analysisId, update.getDurationMs());
        } catch (Exception e) {
            log.error("[CodeAnalysis] projectOverview FAILED id={}", analysisId, e);
            markFailed(analysisId, e.getMessage());
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    @Async
    protected void runCommitReview(Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 10, "克隆仓库中...");
            int depth = dto.getCloneDepth() != null ? dto.getCloneDepth() : 50;
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(), depth);

            updateProgress(analysisId, "SCANNING", 30, "读取提交 diff...");
            String diff = gitRepoService.readCommitDiff(repo, dto.getCommitSha(), MAX_DIFF_BYTES);

            updateProgress(analysisId, "ANALYZING", 60, "调用 LLM 做阿里规约审查...");
            String repoName = extractRepoName(dto.getGitUrl());
            String userPrompt = CodeReviewPrompts.commitReviewUser(repoName, dto.getCommitSha(), diff);

            LlmResponse resp = callLlm(CodeReviewPrompts.commitReviewSystem(), userPrompt);

            updateProgress(analysisId, "RENDERING", 90, "解析 findings 并入库...");
            persistReviewResult(analysisId, resp, start);
            log.info("[CodeAnalysis] commitReview OK id={}", analysisId);
        } catch (Exception e) {
            log.error("[CodeAnalysis] commitReview FAILED id={}", analysisId, e);
            markFailed(analysisId, e.getMessage());
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    @Async
    protected void runDiffReview(Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 10, "克隆仓库中...");
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(), 100);

            updateProgress(analysisId, "SCANNING", 30, "读取 diff...");
            String diff = gitRepoService.readDiff(repo, dto.getBaseRef(), dto.getHeadRef(), MAX_DIFF_BYTES);

            updateProgress(analysisId, "ANALYZING", 60, "调用 LLM 做对比审查...");
            String repoName = extractRepoName(dto.getGitUrl());
            String userPrompt = CodeReviewPrompts.diffReviewUser(repoName,
                    dto.getBaseRef(), dto.getHeadRef(), diff);

            LlmResponse resp = callLlm(CodeReviewPrompts.diffReviewSystem(), userPrompt);

            updateProgress(analysisId, "RENDERING", 90, "解析并入库...");
            persistReviewResult(analysisId, resp, start);
            log.info("[CodeAnalysis] diffReview OK id={}", analysisId);
        } catch (Exception e) {
            log.error("[CodeAnalysis] diffReview FAILED id={}", analysisId, e);
            markFailed(analysisId, e.getMessage());
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    /** 把 LLM 返回 JSON 转成 RepoAnalysis + Finding 写库 */
    private void persistReviewResult(Long analysisId, LlmResponse resp, long start) {
        JsonNode root = parseJson(resp.getContent());

        int high = 0, medium = 0, low = 0;
        List<RepoAnalysisFinding> findings = new ArrayList<>();
        JsonNode findingsNode = root.get("findings");
        if (findingsNode != null && findingsNode.isArray()) {
            for (JsonNode f : findingsNode) {
                RepoAnalysisFinding fe = new RepoAnalysisFinding();
                fe.setAnalysisId(analysisId);
                fe.setLevel(upperOrDefault(getText(f, "level"), "LOW"));
                fe.setCategory(getText(f, "category"));
                fe.setTitle(truncate(getText(f, "title"), 255));
                fe.setFile(getText(f, "file"));
                fe.setLineRange(getText(f, "lineRange"));
                fe.setDescription(getText(f, "description"));
                fe.setSuggestion(getText(f, "suggestion"));
                fe.setCodeSnippet(getText(f, "codeSnippet"));
                fe.setRuleRef(getText(f, "ruleRef"));
                fe.setStatus("UNRESOLVED");
                findings.add(fe);
                switch (fe.getLevel()) {
                    case "HIGH" -> high++;
                    case "MEDIUM" -> medium++;
                    default -> low++;
                }
            }
        }
        for (RepoAnalysisFinding f : findings) findingMapper.insert(f);

        Integer score = root.has("overallScore") && !root.get("overallScore").isNull()
                ? root.get("overallScore").asInt()
                : Math.max(0, 100 - high * 15 - medium * 5 - low);

        RepoAnalysis upd = new RepoAnalysis();
        upd.setId(analysisId);
        upd.setStatus("SUCCESS");
        upd.setStage("DONE");
        upd.setProgressPercent(100);
        upd.setProgressMessage("分析完成");
        upd.setOverallScore(score);
        upd.setHighCount(high);
        upd.setMediumCount(medium);
        upd.setLowCount(low);
        upd.setSummaryMd(getText(root, "summaryMd"));
        upd.setDurationMs(System.currentTimeMillis() - start);
        upd.setLlmTokensUsed(safeTotal(resp));
        analysisMapper.updateById(upd);
    }

    private LlmResponse callLlm(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        LlmConfig cfg = LlmConfig.builder()
                .model(model)
                .temperature(temperature)
                .apiKey(systemApiKey)
                .build();
        return llmRouter.get(LlmProvider.valueOf(defaultProviderCode.toUpperCase()))
                .chat(messages, cfg);
    }

    private void updateProgress(Long id, String stage, int percent, String msg) {
        RepoAnalysis u = new RepoAnalysis();
        u.setId(id);
        u.setStatus("RUNNING");
        u.setStage(stage);
        u.setProgressPercent(percent);
        u.setProgressMessage(msg);
        analysisMapper.updateById(u);
    }

    private void markFailed(Long id, String err) {
        RepoAnalysis u = new RepoAnalysis();
        u.setId(id);
        u.setStatus("FAILED");
        u.setProgressPercent(100);
        u.setErrorMsg(truncate(err, 1000));
        analysisMapper.updateById(u);
    }

    // ================== 查询 ==================

    @Override
    public CodeAnalysisVO getAnalysis(Long userId, Long analysisId) {
        RepoAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null || Boolean.TRUE.equals(a.getIsDeleted() != null && a.getIsDeleted() == 1)) {
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
    public List<RepoCommitVO> listCommits(String gitUrl, String branch, Integer limit) {
        File repo = null;
        try {
            repo = gitRepoService.shallowClone(gitUrl, branch, limit == null ? DEFAULT_COMMIT_LIMIT : limit);
            return gitRepoService.listCommits(repo, limit == null ? DEFAULT_COMMIT_LIMIT : limit);
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
        List<RepoAnalysisFinding> allFindings = findingMapper.selectList(
                new LambdaQueryWrapper<RepoAnalysisFinding>()
                        .in(all.size() > 0,
                            RepoAnalysisFinding::getAnalysisId,
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

    // ================== 工具方法 ==================

    private CodeAnalysisVO toVo(RepoAnalysis a, boolean loadFindings) {
        CodeAnalysisVO v = new CodeAnalysisVO();
        BeanUtils.copyProperties(a, v);
        // 反序列化技术栈 / 语言统计
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

    private JsonNode parseJson(String content) {
        if (content == null) throw new BusinessException("LLM 返回空");
        String cleaned = stripCodeFence(content.trim());
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("[CodeAnalysis] JSON 解析失败，原始输出前 500 字：\n{}",
                    cleaned.substring(0, Math.min(500, cleaned.length())));
            throw new BusinessException("LLM 返回 JSON 解析失败: " + e.getMessage());
        }
    }

    /** 去除 markdown 代码围栏 */
    private static String stripCodeFence(String s) {
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                return s.substring(firstNl + 1, lastFence).trim();
            }
        }
        return s;
    }

    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<T> readJsonList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String getText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String formatLangStats(Map<String, Long> langs) {
        long sum = langs.values().stream().mapToLong(Long::longValue).sum();
        if (sum == 0) return "(无可识别源文件)";
        StringBuilder sb = new StringBuilder();
        langs.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .forEach(e -> sb.append(String.format("- %s: %d 行 (%.1f%%)%n",
                        e.getKey(), e.getValue(), e.getValue() * 100.0 / sum)));
        return sb.toString();
    }

    private List<LanguageStatVO> computeLangStatsVO(Map<String, Long> langs) {
        long sum = langs.values().stream().mapToLong(Long::longValue).sum();
        if (sum == 0) return List.of();
        List<LanguageStatVO> list = new ArrayList<>();
        langs.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> list.add(new LanguageStatVO(e.getKey(), null,
                        e.getValue(), e.getValue() * 100.0 / sum)));
        return list;
    }

    private static String extractRepoName(String gitUrl) {
        if (gitUrl == null) return "unknown";
        String s = gitUrl;
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String upperOrDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s.toUpperCase();
    }

    private static String truncate(String s, int len) {
        if (s == null) return null;
        return s.length() <= len ? s : s.substring(0, len);
    }

    private static Integer safeTotal(LlmResponse r) {
        if (r == null) return null;
        Integer prompt = r.getPromptTokens();
        Integer completion = r.getCompletionTokens();
        if (prompt == null && completion == null) return null;
        return (prompt == null ? 0 : prompt) + (completion == null ? 0 : completion);
    }
}
