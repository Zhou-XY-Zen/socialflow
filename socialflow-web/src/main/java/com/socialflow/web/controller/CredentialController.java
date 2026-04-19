package com.socialflow.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.SaveCredentialDTO;
import com.socialflow.model.dto.SaveCredentialProjectDTO;
import com.socialflow.model.vo.RepoAuthCredentialVO;
import com.socialflow.model.vo.RepoCredentialProjectVO;
import com.socialflow.service.codeanalysis.CredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代码分析 · Git 仓库凭证管理。
 *
 * 路径：/api/v1/code-analysis/credential
 */
@Tag(name = "code-analysis-credential", description = "代码分析 · 仓库凭证")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/code-analysis/credential")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    @Operation(summary = "列出当前用户的所有凭证（token 已掩码）")
    @SaCheckLogin
    @GetMapping
    public R<List<RepoAuthCredentialVO>> list() {
        return R.ok(credentialService.list(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "新增或更新凭证")
    @SaCheckLogin
    @PostMapping
    public R<RepoAuthCredentialVO> save(@Valid @RequestBody SaveCredentialDTO dto) {
        return R.ok(credentialService.save(StpUtil.getLoginIdAsLong(), dto));
    }

    @Operation(summary = "删除凭证")
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        credentialService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "测试凭证连接（调 git ls-remote 校验）")
    @SaCheckLogin
    @PostMapping("/{id}/test")
    public R<RepoAuthCredentialVO> test(@PathVariable Long id) {
        return R.ok(credentialService.test(StpUtil.getLoginIdAsLong(), id));
    }

    // ==================== 子级：凭证下的仓库项目 ====================

    @Operation(summary = "列出某凭证下所有仓库项目")
    @SaCheckLogin
    @GetMapping("/{credentialId}/projects")
    public R<List<RepoCredentialProjectVO>> listProjects(@PathVariable Long credentialId) {
        return R.ok(credentialService.listProjects(StpUtil.getLoginIdAsLong(), credentialId));
    }

    @Operation(summary = "在某凭证下新增/更新仓库项目")
    @SaCheckLogin
    @PostMapping("/{credentialId}/projects")
    public R<RepoCredentialProjectVO> saveProject(@PathVariable Long credentialId,
                                                   @Valid @RequestBody SaveCredentialProjectDTO dto) {
        return R.ok(credentialService.saveProject(StpUtil.getLoginIdAsLong(), credentialId, dto));
    }

    @Operation(summary = "删除某个仓库项目")
    @SaCheckLogin
    @DeleteMapping("/project/{projectId}")
    public R<Void> deleteProject(@PathVariable Long projectId) {
        credentialService.deleteProject(StpUtil.getLoginIdAsLong(), projectId);
        return R.ok();
    }
}
