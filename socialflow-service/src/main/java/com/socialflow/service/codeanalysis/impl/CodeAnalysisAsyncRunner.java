package com.socialflow.service.codeanalysis.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.dao.mapper.LlmCallLogMapper;
import com.socialflow.dao.mapper.RepoAnalysisFindingMapper;
import com.socialflow.dao.mapper.RepoAnalysisMapper;
import com.socialflow.model.dto.AnalyzeRepoDTO;
import com.socialflow.model.entity.LlmCallLog;
import com.socialflow.model.entity.RepoAnalysis;
import com.socialflow.model.entity.RepoAnalysisFinding;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.model.vo.LanguageStatVO;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.codeanalysis.CodeReviewPrompts;
import com.socialflow.service.codeanalysis.CredentialService;
import com.socialflow.service.codeanalysis.GitRepoService;
import com.socialflow.service.codeanalysis.GitRepoService.FileDiff;
import com.socialflow.service.codeanalysis.GitRepoService.SourceFile;
import com.socialflow.service.codeanalysis.RuleLibraryHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码分析异步执行器（Wave 5.5 全量模式）。
 *
 * 设计原则：
 *   1. 项目概览：分层递进 —— 每个模块单独送 LLM 读全部源码 → 最后汇总项目全景
 *   2. 提交审查：按文件分片 —— 每个文件的 diff 单独送 LLM 审查 → 最后合并归纳
 *   3. 每次 LLM 调用独立落 llm_call_log，用于分析详情链路 + 仪表盘统计
 *   4. 进度条粒度细化：显示"正在分析第 3/7 个模块：socialflow-service"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeAnalysisAsyncRunner {

    private final RepoAnalysisMapper analysisMapper;
    private final RepoAnalysisFindingMapper findingMapper;
    private final LlmCallLogMapper llmCallLogMapper;
    private final GitRepoService gitRepoService;
    private final LlmRouter llmRouter;
    private final CredentialService credentialService;
    private final RuleLibraryHolder ruleLibrary;

    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    @Value("${socialflow.code-analysis.provider:DEEPSEEK}")
    private String defaultProviderCode;

    @Value("${socialflow.code-analysis.model:deepseek-chat}")
    private String model;

    @Value("${socialflow.code-analysis.temperature:0.3}")
    private Double temperature;

    /** 单文件源码截断上限（送 LLM 前），比较宽松 */
    private static final int PER_SOURCE_FILE_BYTES = 30 * 1024;
    /** 单模块源码汇总上限（送一次 LLM）——超过就继续按文件拆 */
    private static final int PER_MODULE_BYTES = 80 * 1024;
    /** 单个文件 diff 送 LLM 时的字节软上限，超过提示 AI 关注头部 */
    private static final int PER_FILE_DIFF_HINT_BYTES = 60 * 1024;
    private static final int TREE_DEPTH = 3;

    // ============================================================
    //   项目概览：分层递进全量分析
    // ============================================================

    @Async("codeAnalysisExecutor")
    public void runProjectOverview(Long userId, Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 5, "克隆仓库中...");
            RepoAuthCredential cred = credentialService.resolveForUrl(userId, dto.getGitUrl()).orElse(null);
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(),
                    dto.getCloneDepth() != null ? dto.getCloneDepth() : 1, cred);

            updateProgress(analysisId, "SCANNING", 10, "扫描全部源文件...");
            Map<String, Long> langs = gitRepoService.scanLanguageStats(repo, dto.getExcludeDirs());
            String tree = gitRepoService.buildTreeView(repo, TREE_DEPTH, dto.getExcludeDirs());
            Map<String, List<SourceFile>> modules = gitRepoService.scanSourcesByModule(
                    repo, dto.getExcludeDirs(), PER_SOURCE_FILE_BYTES);

            if (modules.isEmpty()) {
                throw new RuntimeException("仓库内没识别到任何源文件（可能都被 excludeDirs 过滤了）");
            }

            String repoName = extractRepoName(dto.getGitUrl());

            // 逐模块生成摘要
            List<String> moduleSummaries = new ArrayList<>();
            int totalModules = modules.size();
            int idx = 0;
            for (Map.Entry<String, List<SourceFile>> entry : modules.entrySet()) {
                idx++;
                String moduleName = entry.getKey();
                List<SourceFile> files = entry.getValue();

                int pct = 15 + (int) Math.floor(idx * 65.0 / totalModules);  // 15% → 80%
                updateProgress(analysisId, "MODULE_SUMMARY", pct,
                        String.format("分析模块 %d/%d: %s (%d 个文件)", idx, totalModules, moduleName, files.size()));

                String summary = summarizeOneModule(userId, analysisId, repoName, moduleName, files);
                moduleSummaries.add("### " + moduleName + "\n" + summary);
            }

            // 最终汇总
            updateProgress(analysisId, "FINAL", 85, "汇总所有模块摘要生成项目全景...");
            String langStatsText = formatLangStats(langs);
            String moduleSummariesJoined = String.join("\n\n---\n\n", moduleSummaries);
            String userPrompt = CodeReviewPrompts.projectOverviewFinalUser(
                    repoName, tree, langStatsText, moduleSummariesJoined);
            LlmResponse resp = callLlm(
                    userId, analysisId, "FINAL", "生成项目全景报告",
                    CodeReviewPrompts.projectOverviewFinalSystem(), userPrompt);

            updateProgress(analysisId, "RENDERING", 95, "解析结果并入库...");
            JsonNode root = parseJson(resp.getContent());

            RepoAnalysis update = new RepoAnalysis();
            update.setId(analysisId);
            update.setStatus(RepoAnalysis.STATUS_SUCCESS);
            update.setStage("DONE");
            update.setProgressPercent(100);
            update.setProgressMessage("分析完成");
            update.setSummaryMd(getText(root, "summaryMd"));
            update.setMermaidCode(getText(root, "mermaidCode"));
            update.setTechStackJson(writeJson(root.get("techStack")));
            update.setLanguageStatsJson(writeJson(computeLangStatsVO(langs)));
            update.setDurationMs(System.currentTimeMillis() - start);
            update.setLlmTokensUsed(sumTokens(analysisId));
            analysisMapper.updateById(update);

            log.info("[CodeAnalysis] projectOverview OK id={} modules={} duration={}ms totalTokens={}",
                    analysisId, totalModules, update.getDurationMs(), update.getLlmTokensUsed());
        } catch (Exception e) {
            log.error("[CodeAnalysis] projectOverview FAILED id={}", analysisId, e);
            markFailed(analysisId, e.getMessage());
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    /** 为一个模块生成摘要：若该模块体积大，采用"分批读再合并摘要"的内部循环 */
    private String summarizeOneModule(Long userId, Long analysisId, String repoName,
                                      String moduleName, List<SourceFile> files) {
        // 按字节分批：每批拼到 PER_MODULE_BYTES 就结束一批
        List<List<SourceFile>> batches = new ArrayList<>();
        List<SourceFile> current = new ArrayList<>();
        int currentSize = 0;
        for (SourceFile f : files) {
            int sz = f.content.length() + f.path.length() + 50;
            if (currentSize + sz > PER_MODULE_BYTES && !current.isEmpty()) {
                batches.add(current);
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(f);
            currentSize += sz;
        }
        if (!current.isEmpty()) batches.add(current);

        List<String> batchSummaries = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            List<SourceFile> batch = batches.get(i);
            String fileList = buildFileList(batch);
            StringBuilder sources = new StringBuilder();
            for (SourceFile f : batch) {
                sources.append("\n\n// ========== ").append(f.path).append(" (")
                       .append(f.lines).append(" lines) ==========\n")
                       .append(f.content);
            }
            String stage = "MODULE_SUMMARY_" + safeStageSegment(moduleName)
                    + (batches.size() > 1 ? "_p" + (i + 1) : "");
            String label = batches.size() > 1
                    ? String.format("模块 %s 分批摘要 %d/%d", moduleName, i + 1, batches.size())
                    : String.format("模块 %s 摘要", moduleName);

            String user = CodeReviewPrompts.moduleSummaryUser(moduleName, fileList, sources.toString());
            LlmResponse resp = callLlm(userId, analysisId, stage, label,
                    CodeReviewPrompts.moduleSummarySystem(), user);
            batchSummaries.add(resp.getContent());
        }
        // 多批时直接拼接；单批直接用
        return batchSummaries.size() == 1
                ? batchSummaries.get(0)
                : "（本模块内容分 " + batchSummaries.size() + " 批摘要）\n\n" +
                  String.join("\n\n---\n\n", batchSummaries);
    }

    // ============================================================
    //   提交审查：按文件分片
    // ============================================================

    @Async("codeAnalysisExecutor")
    public void runCommitReview(Long userId, Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 5, "克隆仓库中...");
            int depth = dto.getCloneDepth() != null ? dto.getCloneDepth() : 50;
            RepoAuthCredential cred = credentialService.resolveForUrl(userId, dto.getGitUrl()).orElse(null);
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(), depth, cred);

            updateProgress(analysisId, "SCANNING", 10, "读取提交 diff 并按文件切分...");
            List<FileDiff> diffs = gitRepoService.readCommitDiffByFile(repo, dto.getCommitSha());
            if (diffs.isEmpty()) {
                throw new RuntimeException("该提交没有任何文件变更");
            }

            String repoName = extractRepoName(dto.getGitUrl());
            int total = diffs.size();
            List<RepoAnalysisFinding> allFindings = new ArrayList<>();

            for (int i = 0; i < diffs.size(); i++) {
                FileDiff fd = diffs.get(i);
                int pct = 15 + (int) Math.floor((i + 1) * 65.0 / total);
                updateProgress(analysisId, "FILE_REVIEW", pct,
                        String.format("审查文件 %d/%d: %s (%d KB)",
                                i + 1, total, fd.file, fd.bytes / 1024));

                String diffContent = fd.diff;
                if (fd.bytes > PER_FILE_DIFF_HINT_BYTES) {
                    // 单文件 diff 特别大时加个提示，但仍然全量送
                    diffContent += "\n\n// 提示：此文件 diff 较大 (" + (fd.bytes / 1024) + " KB)，请 AI 关注主要模式";
                }
                String userPrompt = CodeReviewPrompts.fileReviewUser(
                        repoName, dto.getCommitSha(), fd.file, diffContent);
                String stage = "FILE_REVIEW_" + safeStageSegment(fd.file);
                try {
                    LlmResponse resp = callLlm(userId, analysisId, stage,
                            "审查文件 " + fd.file,
                            CodeReviewPrompts.fileReviewSystem(), userPrompt);
                    List<RepoAnalysisFinding> parsed = parseFindings(analysisId, resp.getContent(), fd.file, repo);
                    allFindings.addAll(parsed);
                } catch (Exception e) {
                    // 黄山版 2.2.2：catch 必须含异常对象到日志。
                    // 单文件审查失败属于业务可降级故障：跳过此文件继续审查其他文件，
                    // 整体结果中会少一份子审查 → 在 progress 里也累加失败数让用户感知。
                    log.error("[CodeAnalysis] 审查文件 {} 失败，跳过此文件继续审查其他文件", fd.file, e);
                }
            }

            // 去重（同文件+同 title）
            List<RepoAnalysisFinding> dedupFindings = dedupFindings(allFindings);

            // ============ 方案 E：self-check 二次复核 ============
            updateProgress(analysisId, "SELF_CHECK", 80, "AI 自检 findings 置信度...");
            dedupFindings = selfCheckFindings(userId, analysisId, dedupFindings);

            int high = (int) dedupFindings.stream().filter(f -> "HIGH".equals(f.getLevel())).count();
            int med = (int) dedupFindings.stream().filter(f -> "MEDIUM".equals(f.getLevel())).count();
            int low = dedupFindings.size() - high - med;

            // 最终合并 + 总结
            updateProgress(analysisId, "FINAL", 85, "合并所有文件审查结果 + 生成总结...");
            String findingsJsonJoined = findingsAsBrief(dedupFindings);
            String userPrompt = CodeReviewPrompts.reviewMergeUser(repoName, dto.getCommitSha(), findingsJsonJoined);
            LlmResponse resp = callLlm(userId, analysisId, "FINAL", "合并总结",
                    CodeReviewPrompts.reviewMergeSystem(), userPrompt);

            JsonNode root = parseJson(resp.getContent());
            Integer score = root.has("overallScore") && !root.get("overallScore").isNull()
                    ? root.get("overallScore").asInt()
                    : Math.max(0, 100 - high * 15 - med * 5 - low);

            for (RepoAnalysisFinding f : dedupFindings) findingMapper.insert(f);

            updateProgress(analysisId, "RENDERING", 95, "写入结果...");
            RepoAnalysis upd = new RepoAnalysis();
            upd.setId(analysisId);
            upd.setStatus(RepoAnalysis.STATUS_SUCCESS);
            upd.setStage("DONE");
            upd.setProgressPercent(100);
            upd.setProgressMessage("分析完成");
            upd.setOverallScore(score);
            upd.setHighCount(high);
            upd.setMediumCount(med);
            upd.setLowCount(low);
            upd.setSummaryMd(getText(root, "summaryMd"));
            upd.setDurationMs(System.currentTimeMillis() - start);
            upd.setLlmTokensUsed(sumTokens(analysisId));
            analysisMapper.updateById(upd);

            log.info("[CodeAnalysis] commitReview OK id={} files={} findings={} score={} totalTokens={}",
                    analysisId, total, dedupFindings.size(), score, upd.getLlmTokensUsed());
        } catch (Exception e) {
            log.error("[CodeAnalysis] commitReview FAILED id={}", analysisId, e);
            markFailed(analysisId, e.getMessage());
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    // ============================================================
    //   对比分析（DiffReview）：同提交审查，只是 diff 来源不同
    // ============================================================

    @Async("codeAnalysisExecutor")
    public void runDiffReview(Long userId, Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 5, "克隆仓库中...");
            RepoAuthCredential cred = credentialService.resolveForUrl(userId, dto.getGitUrl()).orElse(null);
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(), 100, cred);

            updateProgress(analysisId, "SCANNING", 10, "读取 diff 并按文件切分...");
            List<FileDiff> diffs = gitRepoService.readDiffByFile(repo, dto.getBaseRef(), dto.getHeadRef());
            if (diffs.isEmpty()) {
                throw new RuntimeException("两个 ref 之间没有文件变更");
            }

            String repoName = extractRepoName(dto.getGitUrl());
            int total = diffs.size();
            List<RepoAnalysisFinding> allFindings = new ArrayList<>();

            for (int i = 0; i < diffs.size(); i++) {
                FileDiff fd = diffs.get(i);
                int pct = 15 + (int) Math.floor((i + 1) * 65.0 / total);
                updateProgress(analysisId, "FILE_REVIEW", pct,
                        String.format("审查文件 %d/%d: %s (%d KB)",
                                i + 1, total, fd.file, fd.bytes / 1024));

                String userPrompt = CodeReviewPrompts.fileReviewUser(
                        repoName, dto.getBaseRef() + ".." + dto.getHeadRef(), fd.file, fd.diff);
                String stage = "FILE_REVIEW_" + safeStageSegment(fd.file);
                try {
                    LlmResponse resp = callLlm(userId, analysisId, stage,
                            "审查文件 " + fd.file,
                            CodeReviewPrompts.fileReviewSystem(), userPrompt);
                    allFindings.addAll(parseFindings(analysisId, resp.getContent(), fd.file, repo));
                } catch (Exception e) {
                    // 黄山版 2.2.2：catch 必须含异常对象到日志（diff 审查同 commit 审查的容错策略）
                    log.error("[CodeAnalysis] 审查 diff 文件 {} 失败，跳过此文件继续审查其他文件", fd.file, e);
                }
            }

            List<RepoAnalysisFinding> dedupFindings = dedupFindings(allFindings);

            // 方案 E：self-check 二次复核
            updateProgress(analysisId, "SELF_CHECK", 80, "AI 自检 findings 置信度...");
            dedupFindings = selfCheckFindings(userId, analysisId, dedupFindings);

            int high = (int) dedupFindings.stream().filter(f -> "HIGH".equals(f.getLevel())).count();
            int med = (int) dedupFindings.stream().filter(f -> "MEDIUM".equals(f.getLevel())).count();
            int low = dedupFindings.size() - high - med;

            updateProgress(analysisId, "FINAL", 85, "合并并生成总结...");
            String findingsJsonJoined = findingsAsBrief(dedupFindings);
            String userPrompt = CodeReviewPrompts.reviewMergeUser(
                    repoName, dto.getBaseRef() + ".." + dto.getHeadRef(), findingsJsonJoined);
            LlmResponse resp = callLlm(userId, analysisId, "FINAL", "合并总结",
                    CodeReviewPrompts.reviewMergeSystem(), userPrompt);

            JsonNode root = parseJson(resp.getContent());
            Integer score = root.has("overallScore") && !root.get("overallScore").isNull()
                    ? root.get("overallScore").asInt()
                    : Math.max(0, 100 - high * 15 - med * 5 - low);

            for (RepoAnalysisFinding f : dedupFindings) findingMapper.insert(f);

            updateProgress(analysisId, "RENDERING", 95, "写入结果...");
            RepoAnalysis upd = new RepoAnalysis();
            upd.setId(analysisId);
            upd.setStatus(RepoAnalysis.STATUS_SUCCESS);
            upd.setStage("DONE");
            upd.setProgressPercent(100);
            upd.setProgressMessage("分析完成");
            upd.setOverallScore(score);
            upd.setHighCount(high);
            upd.setMediumCount(med);
            upd.setLowCount(low);
            upd.setSummaryMd(getText(root, "summaryMd"));
            upd.setDurationMs(System.currentTimeMillis() - start);
            upd.setLlmTokensUsed(sumTokens(analysisId));
            analysisMapper.updateById(upd);

            log.info("[CodeAnalysis] diffReview OK id={} files={} findings={}",
                    analysisId, total, dedupFindings.size());
        } catch (Exception e) {
            log.error("[CodeAnalysis] diffReview FAILED id={}", analysisId, e);
            markFailed(analysisId, e.getMessage());
        } finally {
            if (repo != null) gitRepoService.cleanup(repo);
        }
    }

    // ============================================================
    //   LLM 调用封装 + 日志
    // ============================================================

    private LlmResponse callLlm(Long userId, Long analysisId, String stage, String label,
                                String systemPrompt, String userPrompt) {
        long t0 = System.currentTimeMillis();
        LlmProvider providerEnum = LlmProvider.valueOf(defaultProviderCode.toUpperCase());
        LlmResponse resp = null;
        String errorMsg = null;
        boolean success = false;
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(userPrompt));
            // maxTokens 8192 = DeepSeek V3 的 completion 上限；
            // 不设会落到服务端默认 4096，FINAL 阶段要求 2000+ 字总结（≈5000+ tokens）就会被截断，
            // 导致 JSON 没闭合，下游 stripCodeFence + parseJson 也救不回来。
            LlmConfig cfg = LlmConfig.builder()
                    .model(model)
                    .temperature(temperature)
                    .apiKey(systemApiKey)
                    .maxTokens(8192)
                    .build();
            resp = llmRouter.get(providerEnum).chat(messages, cfg);
            success = true;
            return resp;
        } catch (Exception e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            // 无论成功失败都记一条 log
            LlmCallLog logEntry = new LlmCallLog();
            logEntry.setAnalysisId(analysisId);
            logEntry.setUserId(userId);
            logEntry.setStage(stage);
            logEntry.setStageLabel(label);
            logEntry.setProvider(providerEnum.name());
            logEntry.setModel(model);
            if (resp != null) {
                logEntry.setPromptTokens(resp.getPromptTokens());
                logEntry.setCompletionTokens(resp.getCompletionTokens());
                logEntry.setTotalTokens(safeTotal(resp));
            } else {
                logEntry.setPromptTokens(0);
                logEntry.setCompletionTokens(0);
                logEntry.setTotalTokens(0);
            }
            logEntry.setLatencyMs(System.currentTimeMillis() - t0);
            logEntry.setSuccess(success ? 1 : 0);
            logEntry.setErrorMsg(truncate(errorMsg, 500));
            try { llmCallLogMapper.insert(logEntry); }
            catch (Exception ex) { log.warn("[LLM log] 写入失败: {}", ex.getMessage()); }
        }
    }

    /** 汇总某次分析的所有 LLM 调用 token（用于更新 RepoAnalysis.llmTokensUsed） */
    private Integer sumTokens(Long analysisId) {
        try {
            List<LlmCallLog> logs = llmCallLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LlmCallLog>()
                            .eq(LlmCallLog::getAnalysisId, analysisId));
            return logs.stream().mapToInt(l -> l.getTotalTokens() == null ? 0 : l.getTotalTokens()).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    // ============================================================
    //   辅助工具
    // ============================================================

    private void updateProgress(Long id, String stage, int percent, String msg) {
        RepoAnalysis u = new RepoAnalysis();
        u.setId(id);
        u.setStatus(RepoAnalysis.STATUS_RUNNING);
        u.setStage(stage);
        u.setProgressPercent(percent);
        u.setProgressMessage(msg);
        analysisMapper.updateById(u);
    }

    private void markFailed(Long id, String err) {
        RepoAnalysis u = new RepoAnalysis();
        u.setId(id);
        u.setStatus(RepoAnalysis.STATUS_FAILED);
        u.setProgressPercent(100);
        u.setErrorMsg(truncate(err, 1000));
        u.setLlmTokensUsed(sumTokens(id));
        analysisMapper.updateById(u);
    }

    private JsonNode parseJson(String content) {
        if (content == null) throw new RuntimeException("LLM 返回空");
        String raw = content.trim();
        String cleaned = stripCodeFence(raw);
        try {
            return JsonUtil.mapper().readTree(cleaned);
        } catch (Exception e) {
            // 把 raw 和 cleaned 的长度、结尾都打出来，便于判断是"围栏没剥"还是"被截断"
            int rl = raw.length(), cl = cleaned.length();
            String cTail = cleaned.substring(Math.max(0, cl - 80));
            log.warn("[CodeAnalysis] JSON 解析失败 (rawLen={}, cleanedLen={}, cleanedTail={}), cleaned 前 500 字：\n{}",
                    rl, cl, cTail, cleaned.substring(0, Math.min(500, cl)));
            throw new RuntimeException("LLM 返回 JSON 解析失败: " + e.getMessage());
        }
    }

    private List<RepoAnalysisFinding> parseFindings(Long analysisId, String content, String fallbackFile,
                                                    File repoRoot) {
        try {
            JsonNode root = parseJson(content);
            JsonNode findingsNode = root.get("findings");
            if (findingsNode == null || !findingsNode.isArray()) return List.of();
            List<RepoAnalysisFinding> list = new ArrayList<>();
            int dropRule = 0, dropSnippet = 0, dropLine = 0;
            for (JsonNode f : findingsNode) {
                RepoAnalysisFinding fe = new RepoAnalysisFinding();
                fe.setAnalysisId(analysisId);
                fe.setLevel(upperOrDefault(getText(f, "level"), "LOW"));
                fe.setCategory(truncate(getText(f, "category"), 60));
                fe.setTitle(truncate(getText(f, "title"), 255));
                String file = getText(f, "file");
                fe.setFile(truncate(file != null && !file.isBlank() ? file : fallbackFile, 500));
                fe.setLineRange(truncate(getText(f, "lineRange"), 60));
                fe.setDescription(getText(f, "description"));
                fe.setSuggestion(getText(f, "suggestion"));
                fe.setCodeSnippet(getText(f, "codeSnippet"));
                fe.setRuleRef(truncate(getText(f, "ruleRef"), 250));
                fe.setStatus("UNRESOLVED");

                // ============ 三层反向校验（A + B + C）============
                // B：ruleRef 必须是黄山版 321 条里真实存在的编号
                if (!ruleLibrary.isValidRuleRef(fe.getRuleRef())) {
                    dropRule++;
                    log.debug("[Validate] 丢弃 finding: ruleRef 不在白名单 ref={} title={}",
                            fe.getRuleRef(), fe.getTitle());
                    continue;
                }
                // A：codeSnippet 必须能在源文件中 contains 找到
                if (!validateCodeSnippet(repoRoot, fe.getFile(), fe.getCodeSnippet())) {
                    dropSnippet++;
                    log.debug("[Validate] 丢弃 finding: codeSnippet 在原文中找不到 file={} title={}",
                            fe.getFile(), fe.getTitle());
                    continue;
                }
                // C：lineRange 对应行必须是真实代码（非注释/空行/纯花括号）
                if (!validateLineRange(repoRoot, fe.getFile(), fe.getLineRange())) {
                    dropLine++;
                    log.debug("[Validate] 丢弃 finding: lineRange 指向注释/空行/纯括号 file={} line={} title={}",
                            fe.getFile(), fe.getLineRange(), fe.getTitle());
                    continue;
                }
                list.add(fe);
            }
            int total = findingsNode.size();
            int kept = list.size();
            if (dropRule + dropSnippet + dropLine > 0) {
                log.info("[Validate] {} → {} 条 (丢: ruleRef={}, codeSnippet={}, lineRange={})",
                        total, kept, dropRule, dropSnippet, dropLine);
            }
            return list;
        } catch (Exception e) {
            log.warn("[CodeAnalysis] 解析 findings 失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ============================================================
    //   方案 A/B/C：三层 finding 反向校验
    // ============================================================

    /** A：codeSnippet 必须能在原文中找到（去除空白后比较）。snippet 为空也算 fail，强制 LLM 给。 */
    private boolean validateCodeSnippet(File repoRoot, String filePath, String snippet) {
        if (snippet == null || snippet.isBlank()) return false;
        if (repoRoot == null) return true; // 无法校验时不丢
        try {
            File f = new File(repoRoot, filePath);
            if (!f.isFile() || !f.canRead()) return true; // 读不到时不丢
            String src = java.nio.file.Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            // 标准化：去掉所有空白后比较，容忍 LLM 改空格/换行
            String snipNorm = snippet.replaceAll("\\s+", "");
            String srcNorm = src.replaceAll("\\s+", "");
            // 至少前 30 个字符要在源文件里
            String key = snipNorm.length() > 30 ? snipNorm.substring(0, 30) : snipNorm;
            return srcNorm.contains(key);
        } catch (Exception e) {
            return true; // 读文件出错时不丢，避免误杀
        }
    }

    /** C：lineRange 对应行必须是有内容的代码行（不是单纯注释/空行/单括号）。 */
    private boolean validateLineRange(File repoRoot, String filePath, String lineRange) {
        if (lineRange == null || lineRange.isBlank()) return true; // 没给行号不强校
        if (repoRoot == null) return true;
        try {
            File f = new File(repoRoot, filePath);
            if (!f.isFile() || !f.canRead()) return true;
            // 解析 lineRange 头部第一个数字
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(lineRange);
            if (!m.find()) return true;
            int line = Integer.parseInt(m.group(1));
            if (line <= 0) return true;
            // 读到对应行
            List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            // 容忍 ±2 行偏移：在 [line-2, line+2] 范围内只要有"实际代码行"就算过
            int from = Math.max(1, line - 2);
            int to = Math.min(lines.size(), line + 2);
            for (int i = from; i <= to; i++) {
                String s = lines.get(i - 1).trim();
                if (s.isEmpty()) continue;
                if (s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")) continue;
                if (s.equals("{") || s.equals("}") || s.equals("};") || s.equals("})") || s.equals("});")) continue;
                return true; // 找到一行真实代码
            }
            return false;
        } catch (Exception e) {
            return true; // 出错时不丢
        }
    }

    private List<RepoAnalysisFinding> dedupFindings(List<RepoAnalysisFinding> all) {
        Map<String, RepoAnalysisFinding> dedup = new LinkedHashMap<>();
        for (RepoAnalysisFinding f : all) {
            String key = (f.getFile() == null ? "" : f.getFile()) + "|"
                    + (f.getLineRange() == null ? "" : f.getLineRange()) + "|"
                    + (f.getTitle() == null ? "" : f.getTitle());
            dedup.putIfAbsent(key, f);
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 方案 E：Self-check —— 把当前 findings 整体喂给 LLM 让它以"质疑者"身份打分，
     * confidence < 60 的 finding 丢弃。
     *
     * 失败时（LLM 错误/超时）原样返回，不影响主流程。
     */
    private List<RepoAnalysisFinding> selfCheckFindings(Long userId, Long analysisId,
                                                        List<RepoAnalysisFinding> findings) {
        if (findings == null || findings.isEmpty()) return findings;
        // 大于 100 条不自检（成本太高），直接返回
        if (findings.size() > 100) {
            log.info("[SelfCheck] findings 数量 {} > 100，跳过自检", findings.size());
            return findings;
        }
        try {
            // 把 findings 简化为 LLM 看得懂的 JSON 数组
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < findings.size(); i++) {
                RepoAnalysisFinding f = findings.get(i);
                if (i > 0) json.append(",\n");
                json.append(String.format(
                        "  {\"index\":%d,\"file\":%s,\"line\":%s,\"title\":%s,\"ruleRef\":%s,\"codeSnippet\":%s}",
                        i,
                        jsonStr(f.getFile()), jsonStr(f.getLineRange()),
                        jsonStr(f.getTitle()), jsonStr(f.getRuleRef()),
                        jsonStr(truncate(f.getCodeSnippet(), 300))));
            }
            json.append("\n]");

            LlmResponse resp = callLlm(userId, analysisId, "SELF_CHECK", "二次复核 findings 置信度",
                    CodeReviewPrompts.findingSelfCheckSystem(),
                    CodeReviewPrompts.findingSelfCheckUser(json.toString()));

            JsonNode root = parseJson(resp.getContent());
            JsonNode checks = root.get("checks");
            if (checks == null || !checks.isArray()) return findings;

            // 默认全保留，被标记 false_positive 或 confidence<60 的丢弃
            boolean[] keep = new boolean[findings.size()];
            for (int i = 0; i < keep.length; i++) keep[i] = true;
            int dropped = 0;
            for (JsonNode c : checks) {
                int idx = c.path("index").asInt(-1);
                if (idx < 0 || idx >= findings.size()) continue;
                int conf = c.path("confidence").asInt(100);
                String verdict = c.path("verdict").asText("valid");
                if ("false_positive".equalsIgnoreCase(verdict) || conf < 60) {
                    keep[idx] = false;
                    dropped++;
                }
            }
            List<RepoAnalysisFinding> survivors = new ArrayList<>();
            for (int i = 0; i < findings.size(); i++) {
                if (keep[i]) survivors.add(findings.get(i));
            }
            log.info("[SelfCheck] {} → {} 条（丢弃 {} 条置信度低/误判）",
                    findings.size(), survivors.size(), dropped);
            return survivors;
        } catch (Exception e) {
            log.warn("[SelfCheck] 自检失败，跳过过滤: {}", e.getMessage());
            return findings;
        }
    }

    /** JSON 字符串转义辅助（双引号包裹 + 转义内部双引号 + 换行） */
    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    /** 把 findings 简要描述拼起来给最终 merge LLM 调用用 */
    private String findingsAsBrief(List<RepoAnalysisFinding> findings) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < findings.size(); i++) {
            RepoAnalysisFinding f = findings.get(i);
            sb.append(String.format("[%d] %s | %s | %s:%s | %s%n",
                    i + 1, f.getLevel(),
                    f.getCategory() == null ? "-" : f.getCategory(),
                    f.getFile() == null ? "-" : f.getFile(),
                    f.getLineRange() == null ? "-" : f.getLineRange(),
                    f.getTitle()));
        }
        return sb.toString();
    }

    private String buildFileList(List<SourceFile> files) {
        StringBuilder sb = new StringBuilder();
        for (SourceFile f : files) {
            sb.append("- ").append(f.path).append(" (").append(f.lines).append(" lines)\n");
        }
        return sb.toString();
    }

    /**
     * 剥 LLM 返回里的各种包装，返回可能的 JSON 主体。策略优先级：
     *  1) 完整 ```...``` 围栏 → 取中间段
     *  2) 残缺 ```...（只开头没闭合，被 max_tokens 截断常见）→ 砍掉首行
     *  3) 兜底：找第一个 {/[ 到最后一个 }/] 之间
     * 无论如何都尽力返回一个可被 Jackson 尝试解析的字符串。
     */
    private static String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();

        // 1) 完整围栏：```json\n{...}\n```
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                t = t.substring(firstNl + 1, lastFence).trim();
            } else if (firstNl > 0) {
                // 2) 只有开头围栏没闭合（被截断）→ 砍掉第一行
                t = t.substring(firstNl + 1).trim();
            }
        }

        // 3) 兜底：提取第一个 {/[ 到最后一个 }/] 之间的内容
        int startObj = t.indexOf('{');
        int startArr = t.indexOf('[');
        int start;
        if (startObj < 0) start = startArr;
        else if (startArr < 0) start = startObj;
        else start = Math.min(startObj, startArr);

        int endObj = t.lastIndexOf('}');
        int endArr = t.lastIndexOf(']');
        int end = Math.max(endObj, endArr);

        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return JsonUtil.mapper().writeValueAsString(obj);
        } catch (Exception e) {
            // 黄山版 2.2.2：catch 后至少记日志，避免静默失败
            log.error("[CodeAnalysis] writeJson 序列化失败: {}", obj.getClass().getSimpleName(), e);
            return null;
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
        Integer p = r.getPromptTokens();
        Integer c = r.getCompletionTokens();
        if (p == null && c == null) return null;
        return (p == null ? 0 : p) + (c == null ? 0 : c);
    }

    /** 把阶段标识里的特殊字符替换，保证 VARCHAR 64 装得下 */
    private static String safeStageSegment(String raw) {
        if (raw == null) return "unknown";
        String s = raw.replaceAll("[^A-Za-z0-9\\-_.]", "_");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
