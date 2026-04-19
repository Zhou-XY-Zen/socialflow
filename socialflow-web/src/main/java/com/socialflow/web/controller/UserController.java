package com.socialflow.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.LoginDTO;
import com.socialflow.model.dto.RegisterDTO;
import com.socialflow.model.vo.LoginVO;
import com.socialflow.model.vo.UserVO;
import com.socialflow.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户认证控制器 —— 处理用户的注册、登录、登出和获取当前用户信息。
 *
 * 本控制器处理的基础 URL 路径为 {@code /api/v1/auth}，提供以下功能：
 *     - 用户注册（无需登录即可访问）
 *     - 用户登录（无需登录即可访问）
 *     - 获取当前登录用户信息（需登录）
 *     - 用户登出（需登录）
 *
 * 本系统使用 Sa-Token 框架进行认证管理。Sa-Token 是一个轻量级的 Java 权限认证框架，
 * 提供了登录认证、权限验证、Session 会话等功能。
 *
 * 使用的 HTTP 方法：
 *     - POST —— 注册、登录、登出（写操作）
 *     - GET  —— 获取当前用户信息（读操作）
 *
 * @see UserService 用户业务逻辑的具体实现
 */
/*
 * @Tag           —— Swagger 文档分组标签，显示为 "auth"（认证相关）
 * @RestController —— REST 控制器
 * @RequestMapping —— 公共路径前缀：/api/v1/auth
 * @RequiredArgsConstructor —— Lombok 自动注入 final 依赖
 */
@Tag(name = "auth")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/auth")
@RequiredArgsConstructor
public class UserController {

    /** 用户服务，封装注册、登录、查询用户等业务逻辑 */
    private final UserService userService;

    /**
     * 用户注册。
     *
     * 接口路径：POST /api/v1/auth/register
     *
     * 功能：新用户注册账号。注册成功后返回用户信息（不含密码）。
     *
     * @SaIgnore 注解表示此接口跳过 Sa-Token 的登录校验，
     * 因为用户在注册时尚未登录，所以必须允许匿名访问。
     *
     * @param dto 注册请求参数（包含用户名、密码、邮箱等），使用 @Valid 进行参数校验
     * @return 统一响应体 R，包含注册成功的用户信息 UserVO
     */
    @SaIgnore
    @Operation(summary = "register")
    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(userService.register(dto));
    }

    /**
     * 用户登录。
     *
     * 接口路径：POST /api/v1/auth/login
     *
     * 功能：用户输入用户名和密码进行登录。登录成功后返回 Token（令牌），
     * 前端需要在后续请求的 Header 中携带此 Token 来证明身份。
     *
     * @SaIgnore 注解跳过登录校验，因为登录接口本身不需要已登录状态。
     *
     * @param dto 登录请求参数（包含用户名和密码）
     * @return 统一响应体 R，包含登录信息 LoginVO（含 Token、用户基本信息等）
     */
    @SaIgnore
    @Operation(summary = "login")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(userService.login(dto));
    }

    /**
     * 获取当前登录用户信息。
     *
     * 接口路径：GET /api/v1/auth/me
     *
     * 功能：返回当前已登录用户的详细信息。前端通常在页面加载时调用此接口
     * 来获取用户信息（如头像、昵称等），用于页面展示。
     *
     * 此接口需要登录后才能访问，通过 Sa-Token 的 {@code StpUtil.getLoginIdAsLong()}
     * 自动获取当前用户 ID。
     *
     * @return 统一响应体 R，包含当前用户信息 UserVO
     */
    @Operation(summary = "current user")
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.currentUser(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 用户登出。
     *
     * 接口路径：POST /api/v1/auth/logout
     *
     * 功能：退出当前登录状态。服务端会清除 Sa-Token 中该用户的会话信息，
     * 之后该 Token 将失效，前端需要清除本地保存的 Token。
     *
     * @return 统一响应体 R，无数据体（仅表示登出操作成功）
     */
    @Operation(summary = "logout")
    @PostMapping("/logout")
    public R<Void> logout() {
        userService.logout(StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    /**
     * 更新当前用户的个人资料。
     *
     * 接口路径：PUT /api/v1/auth/profile
     *
     * 功能：更新当前登录用户的昵称和/或头像 URL。
     * 请求体为 JSON 对象，可包含 nickname 和 avatarUrl 字段。
     *
     * @param body 包含 nickname 和/或 avatarUrl 的 Map
     * @return 统一响应体 R，包含更新后的用户信息 UserVO
     */
    @Operation(summary = "update profile")
    @PutMapping("/profile")
    public R<UserVO> updateProfile(@RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String nickname = body.get("nickname");
        String avatarUrl = body.get("avatarUrl");
        return R.ok(userService.updateProfile(userId, nickname, avatarUrl));
    }

    /**
     * 上传用户头像。
     *
     * 接口路径：POST /api/v1/auth/avatar
     *
     * 功能：接收头像文件并上传到对象存储（腾讯云 COS），
     * 同时更新用户表中的头像 URL 字段。
     *
     * @param file 头像图片文件
     * @return 统一响应体 R，包含头像的访问 URL
     */
    @Operation(summary = "upload avatar")
    @PostMapping("/avatar")
    public R<String> uploadAvatar(@RequestPart("file") MultipartFile file) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.uploadAvatar(userId, file));
    }
}
