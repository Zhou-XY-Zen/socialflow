package com.socialflow.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.AnalyzeRepoDTO;
import com.socialflow.model.dto.FindingStatusDTO;
import com.socialflow.model.dto.SaveBookmarkDTO;
import com.socialflow.model.vo.AnalysisStatsVO;
import com.socialflow.model.vo.CodeAnalysisVO;
import com.socialflow.model.entity.RuleLibraryItem;
import com.socialflow.model.vo.LlmCallLogVO;
import com.socialflow.model.vo.RepoBookmarkVO;
import com.socialflow.model.vo.RepoCommitVO;
import com.socialflow.model.vo.RuleLibraryItemVO;
import com.socialflow.service.codeanalysis.CodeAnalysisExportService;
import com.socialflow.service.codeanalysis.CodeAnalysisService;
import com.socialflow.service.codeanalysis.RuleLibraryHolder;
import com.socialflow.service.codeanalysis.RuleLibraryService;
import com.socialflow.service.codeanalysis.ShareTokenRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 代码分析 —— 独立一级菜单下的 8 个二级功能聚合入口。
 *
 * 按二级菜单组织接口：
 *   1. 仪表盘        /dashboard/stats
 *   2. 项目概览      /project (POST trigger) + /{id} (GET 结果)
 *   3. 提交审查      /commits (POST 拉 commit) + /review (POST trigger)
 *   4. 对比分析      /diff (POST trigger)
 *   5. 历史记录      /history (GET 分页) + DELETE / favorite / share
 *   6. 仓库收藏      /bookmark CRUD
 *   7. 规约库        一期返回内置精选（由前端直接展示）
 *   8. 设置          一期走前端本地存储，不需要后端
 *
 * 还有：单条 finding 状态更新 + 分享链接公开访问。
 */
@Tag(name = "code-analysis", description = "代码分析")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/code-analysis")
@RequiredArgsConstructor
public class CodeAnalysisController {

    private final CodeAnalysisService codeAnalysisService;
    private final CodeAnalysisExportService exportService;
    private final RuleLibraryService ruleLibraryService;
    private final RuleLibraryHolder ruleLibraryHolder;
    private final ShareTokenRateLimiter shareRateLimiter;

    // ---------- 仪表盘 ----------

    @Operation(summary = "仪表盘聚合统计")
    @SaCheckLogin
    @GetMapping("/dashboard/stats")
    public R<AnalysisStatsVO> dashboardStats() {
        return R.ok(codeAnalysisService.dashboardStats(StpUtil.getLoginIdAsLong()));
    }

    // ---------- 项目概览 ----------

    @Operation(summary = "触发项目概览分析（异步，立即返回 id）")
    @SaCheckLogin
    @PostMapping("/project")
    public R<Map<String, Long>> triggerProject(@Valid @RequestBody AnalyzeRepoDTO dto) {
        Long id = codeAnalysisService.triggerProjectOverview(StpUtil.getLoginIdAsLong(), dto);
        return R.ok(Map.of("id", id));
    }

    // ---------- 提交审查 ----------

    @Operation(summary = "拉取仓库最近 commit 列表（同步）")
    @SaCheckLogin
    @PostMapping("/commits")
    public R<List<RepoCommitVO>> listCommits(@Valid @RequestBody AnalyzeRepoDTO dto) {
        return R.ok(codeAnalysisService.listCommits(
                StpUtil.getLoginIdAsLong(), dto.getGitUrl(), dto.getBranch(), 50));
    }

    @Operation(summary = "触发提交审查分析（异步）")
    @SaCheckLogin
    @PostMapping("/review")
    public R<Map<String, Long>> triggerReview(@Valid @RequestBody AnalyzeRepoDTO dto) {
        Long id = codeAnalysisService.triggerCommitReview(StpUtil.getLoginIdAsLong(), dto);
        return R.ok(Map.of("id", id));
    }

    // ---------- 对比分析 ----------

    @Operation(summary = "触发对比分析（异步）")
    @SaCheckLogin
    @PostMapping("/diff")
    public R<Map<String, Long>> triggerDiff(@Valid @RequestBody AnalyzeRepoDTO dto) {
        Long id = codeAnalysisService.triggerDiffReview(StpUtil.getLoginIdAsLong(), dto);
        return R.ok(Map.of("id", id));
    }

    // ---------- 通用：查结果 ----------

    @Operation(summary = "获取分析结果（含进度）")
    @SaCheckLogin
    @GetMapping("/{id}")
    public R<CodeAnalysisVO> get(@PathVariable Long id) {
        return R.ok(codeAnalysisService.getAnalysis(StpUtil.getLoginIdAsLong(), id));
    }

    @Operation(summary = "查看某次分析的 LLM 调用链路（分模块/分文件）")
    @SaCheckLogin
    @GetMapping("/{id}/llm-calls")
    public R<List<LlmCallLogVO>> llmCalls(@PathVariable Long id) {
        return R.ok(codeAnalysisService.listLlmCalls(StpUtil.getLoginIdAsLong(), id));
    }

    // ---------- 历史记录 ----------

    @Operation(summary = "分页查询历史")
    @SaCheckLogin
    @GetMapping("/history")
    public R<Page<CodeAnalysisVO>> history(
            @RequestParam(required = false, defaultValue = "1") Integer current,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String analysisType,
            @RequestParam(required = false) String keyword) {
        return R.ok(codeAnalysisService.history(
                StpUtil.getLoginIdAsLong(), current, size, analysisType, keyword));
    }

    @Operation(summary = "删除记录")
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        codeAnalysisService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "切换收藏状态")
    @SaCheckLogin
    @PostMapping("/{id}/favorite")
    public R<Void> toggleFavorite(@PathVariable Long id) {
        codeAnalysisService.toggleFavorite(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "生成分享 token")
    @SaCheckLogin
    @PostMapping("/{id}/share")
    public R<Map<String, String>> share(@PathVariable Long id) {
        String token = codeAnalysisService.generateShareToken(StpUtil.getLoginIdAsLong(), id);
        return R.ok(Map.of("shareToken", token));
    }

    @Operation(summary = "公开访问分享链接（无需登录）")
    @GetMapping("/shared/{token}")
    public R<CodeAnalysisVO> shared(@PathVariable String token, HttpServletRequest request) {
        // 防爬虫/暴力枚举：按 IP+UA 维度做分钟级滑动窗口限流
        String clientKey = clientFingerprint(request);
        if (!shareRateLimiter.allow(clientKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "访问过于频繁，请稍后再试");
        }
        return R.ok(codeAnalysisService.getByShareToken(token));
    }

    /** 生成限流维度的客户端指纹；尊重反向代理头，失败回退 remoteAddr。 */
    private static String clientFingerprint(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            ip = (comma > 0 ? ip.substring(0, comma) : ip).trim();
        } else {
            ip = req.getHeader("X-Real-IP");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        }
        String ua = req.getHeader("User-Agent");
        return ip + "|" + (ua == null ? "-" : Integer.toHexString(ua.hashCode()));
    }

    // ---------- 导出 ----------

    @Operation(summary = "导出分析结果（支持 markdown / html / json）")
    @SaCheckLogin
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "markdown") String format) {
        CodeAnalysisExportService.ExportArtifact art = exportService.export(
                StpUtil.getLoginIdAsLong(), id, parseFormat(format));
        return downloadResponse(art);
    }

    @Operation(summary = "导出分享链接对应的分析（无需登录，沿用分享限流）")
    @GetMapping("/shared/{token}/export")
    public ResponseEntity<byte[]> exportShared(@PathVariable String token,
                                               @RequestParam(defaultValue = "markdown") String format,
                                               HttpServletRequest request) {
        String clientKey = clientFingerprint(request);
        if (!shareRateLimiter.allow(clientKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "访问过于频繁，请稍后再试");
        }
        CodeAnalysisExportService.ExportArtifact art = exportService.exportByShareToken(token, parseFormat(format));
        return downloadResponse(art);
    }

    private static CodeAnalysisExportService.Format parseFormat(String raw) {
        String f = raw == null ? "markdown" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "md", "markdown" -> CodeAnalysisExportService.Format.MARKDOWN;
            case "html", "htm" -> CodeAnalysisExportService.Format.HTML;
            // PDF 已移到前端 html2pdf.js 生成，服务端只支持 md/html
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的导出格式：" + raw + "（服务端仅支持 markdown / html；PDF 由前端生成）");
        };
    }

    private static ResponseEntity<byte[]> downloadResponse(CodeAnalysisExportService.ExportArtifact art) {
        // 文件名走 RFC 5987 编码以支持中文；同时提供 ASCII 备份名
        String encoded = URLEncoder.encode(art.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "attachment; filename=\"" + art.filename() + "\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(art.contentType()))
                .body(art.content());
    }

    // ---------- Finding 单条状态 ----------

    @Operation(summary = "更新单条 finding 状态（已修复/忽略/待跟进）")
    @SaCheckLogin
    @PutMapping("/finding/{id}/status")
    public R<Void> updateFindingStatus(@PathVariable("id") Long findingId,
                                       @Valid @RequestBody FindingStatusDTO dto) {
        codeAnalysisService.updateFindingStatus(StpUtil.getLoginIdAsLong(), findingId, dto);
        return R.ok();
    }

    // ---------- 仓库收藏 ----------

    @Operation(summary = "列出当前用户收藏")
    @SaCheckLogin
    @GetMapping("/bookmark")
    public R<List<RepoBookmarkVO>> listBookmarks() {
        return R.ok(codeAnalysisService.listBookmarks(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "新增或更新收藏")
    @SaCheckLogin
    @PostMapping("/bookmark")
    public R<RepoBookmarkVO> saveBookmark(@Valid @RequestBody SaveBookmarkDTO dto) {
        return R.ok(codeAnalysisService.saveBookmark(StpUtil.getLoginIdAsLong(), dto));
    }

    @Operation(summary = "删除收藏")
    @SaCheckLogin
    @DeleteMapping("/bookmark/{id}")
    public R<Void> deleteBookmark(@PathVariable Long id) {
        codeAnalysisService.deleteBookmark(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    // ---------- 规约库（Wave 7）----------

    @Operation(summary = "列出规约（可按大类/级别/关键词过滤）")
    @SaCheckLogin
    @GetMapping("/rules")
    public R<List<RuleLibraryItemVO>> listRules(
            @RequestParam(required = false) String topCategory,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabledOnly) {
        return R.ok(ruleLibraryService.list(topCategory, level, keyword, enabledOnly));
    }

    @Operation(summary = "启停某条规约")
    @SaCheckLogin
    @PutMapping("/rules/{id}/enabled")
    public R<Void> toggleRuleEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        ruleLibraryService.toggleEnabled(id, enabled);
        ruleLibraryHolder.reloadFromDb();
        return R.ok();
    }

    @Operation(summary = "新增或编辑规约（自定义规约用）")
    @SaCheckLogin
    @PostMapping("/rules")
    public R<RuleLibraryItemVO> saveRule(@Valid @RequestBody RuleLibraryItem item) {
        RuleLibraryItemVO saved = ruleLibraryService.save(item);
        ruleLibraryHolder.reloadFromDb();
        return R.ok(saved);
    }

    @Operation(summary = "删除自定义规约（黄山版内置规约禁删）")
    @SaCheckLogin
    @DeleteMapping("/rules/{id}")
    public R<Void> deleteRule(@PathVariable Long id) {
        ruleLibraryService.deleteCustom(id);
        ruleLibraryHolder.reloadFromDb();
        return R.ok();
    }
}
