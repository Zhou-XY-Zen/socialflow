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
import com.socialflow.model.vo.RepoBookmarkVO;
import com.socialflow.model.vo.RepoCommitVO;
import com.socialflow.service.codeanalysis.CodeAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public R<CodeAnalysisVO> shared(@PathVariable String token) {
        return R.ok(codeAnalysisService.getByShareToken(token));
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
}
