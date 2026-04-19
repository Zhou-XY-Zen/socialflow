package com.socialflow.service.codeanalysis.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.dao.mapper.RepoAnalysisFindingMapper;
import com.socialflow.dao.mapper.RepoAnalysisMapper;
import com.socialflow.model.dto.AnalyzeRepoDTO;
import com.socialflow.model.entity.RepoAnalysis;
import com.socialflow.model.entity.RepoAnalysisFinding;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.service.codeanalysis.CredentialService;
import com.socialflow.model.vo.LanguageStatVO;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.codeanalysis.CodeReviewPrompts;
import com.socialflow.service.codeanalysis.GitRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 代码分析异步执行器 —— 专门承载 @Async 方法的独立 bean。
 *
 * 【为什么单独一个类？】
 * Spring 的 @Async 通过 AOP 代理实现，同类内部方法调用（this.foo()）不会走代理，
 * 导致 @Async 失效，方法在调用方线程里同步执行。把 async 方法拆到独立 @Component
 * 让 {@link CodeAnalysisServiceImpl} 以外部调用的方式触发，代理才会生效。
 *
 * 【线程池】
 * 使用 Spring 默认的 SimpleAsyncTaskExecutor（每次新建线程）。生产环境如需限流
 * 或固定线程池，可在 @EnableAsync 上提供自定义 Executor Bean。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeAnalysisAsyncRunner {

    private final RepoAnalysisMapper analysisMapper;
    private final RepoAnalysisFindingMapper findingMapper;
    private final GitRepoService gitRepoService;
    private final LlmRouter llmRouter;
    private final CredentialService credentialService;

    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    @Value("${socialflow.code-analysis.provider:DEEPSEEK}")
    private String defaultProviderCode;

    @Value("${socialflow.code-analysis.model:deepseek-chat}")
    private String model;

    @Value("${socialflow.code-analysis.temperature:0.3}")
    private Double temperature;

    private static final int MAX_DIFF_BYTES = 40 * 1024;
    private static final int MAX_KEY_FILES_BYTES = 30 * 1024;
    private static final int TREE_DEPTH = 3;

    @Async
    public void runProjectOverview(Long userId, Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 10, "克隆仓库中...");
            RepoAuthCredential cred = credentialService.resolveForUrl(userId, dto.getGitUrl()).orElse(null);
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(),
                    dto.getCloneDepth() != null ? dto.getCloneDepth() : 1, cred);

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
    public void runCommitReview(Long userId, Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 10, "克隆仓库中...");
            int depth = dto.getCloneDepth() != null ? dto.getCloneDepth() : 50;
            RepoAuthCredential cred = credentialService.resolveForUrl(userId, dto.getGitUrl()).orElse(null);
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(), depth, cred);

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
    public void runDiffReview(Long userId, Long analysisId, AnalyzeRepoDTO dto) {
        long start = System.currentTimeMillis();
        File repo = null;
        try {
            updateProgress(analysisId, "CLONING", 10, "克隆仓库中...");
            RepoAuthCredential cred = credentialService.resolveForUrl(userId, dto.getGitUrl()).orElse(null);
            repo = gitRepoService.shallowClone(dto.getGitUrl(), dto.getBranch(), 100, cred);

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

    // ================== 内部工具（供三个 @Async 方法共用） ==================

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

    private JsonNode parseJson(String content) {
        if (content == null) throw new RuntimeException("LLM 返回空");
        String cleaned = stripCodeFence(content.trim());
        try {
            return JsonUtil.mapper().readTree(cleaned);
        } catch (Exception e) {
            log.warn("[CodeAnalysis] JSON 解析失败，原始输出前 500 字：\n{}",
                    cleaned.substring(0, Math.min(500, cleaned.length())));
            throw new RuntimeException("LLM 返回 JSON 解析失败: " + e.getMessage());
        }
    }

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
            return JsonUtil.mapper().writeValueAsString(obj);
        } catch (Exception e) {
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
        Integer prompt = r.getPromptTokens();
        Integer completion = r.getCompletionTokens();
        if (prompt == null && completion == null) return null;
        return (prompt == null ? 0 : prompt) + (completion == null ? 0 : completion);
    }
}
