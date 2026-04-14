package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.result.R;
import com.socialflow.service.user.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API 密钥管理控制器 —— 处理用户 AI 服务密钥的增删查。
 *
 * 基础 URL 路径：{@code /api/v1/api-keys}
 *
 * 提供以下功能：
 *     - 查询用户所有 API Key 的脱敏列表
 *     - 保存（新增或更新）API Key
 *     - 删除指定供应商的 API Key
 */
@Tag(name = "api-keys")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * 获取当前用户的 API Key 脱敏列表。
     *
     * 接口路径：GET /api/v1/api-keys/list
     *
     * @return 脱敏后的 API Key 列表
     */
    @Operation(summary = "list masked api keys")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(apiKeyService.listMasked(userId));
    }

    /**
     * 保存（新增或更新）API Key。
     *
     * 接口路径：POST /api/v1/api-keys/save
     *
     * @param body 请求体，包含 provider、apiKey、baseUrl、isDefault
     * @return 无数据体，表示保存成功
     */
    @Operation(summary = "save api key")
    @PostMapping("/save")
    public R<Void> save(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        LlmProvider provider = LlmProvider.valueOf(((String) body.get("provider")).toUpperCase());
        String apiKey = (String) body.get("apiKey");
        String baseUrl = (String) body.get("baseUrl");
        boolean isDefault = Boolean.TRUE.equals(body.get("isDefault"));

        apiKeyService.saveKey(userId, provider, apiKey, baseUrl, isDefault);
        return R.ok();
    }

    /**
     * 删除指定供应商的 API Key。
     *
     * 接口路径：DELETE /api/v1/api-keys/{provider}
     *
     * @param provider 供应商名称（如 DEEPSEEK、OPENAI 等）
     * @return 无数据体，表示删除成功
     */
    @Operation(summary = "delete api key")
    @DeleteMapping("/{provider}")
    public R<Void> delete(@PathVariable String provider) {
        Long userId = StpUtil.getLoginIdAsLong();
        apiKeyService.delete(userId, LlmProvider.valueOf(provider.toUpperCase()));
        return R.ok();
    }
}
