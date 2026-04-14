package com.socialflow.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户登录请求 DTO —— 用于用户登录认证的接口入参
 *
 * 【作用】封装用户登录时提交的凭证信息（邮箱和密码）。
 *   后端接收后会校验邮箱是否存在以及密码是否正确，
 *   验证通过后返回 JWT Token。
 *
 * 【对应 API 接口】
 *   POST /api/auth/login  —— 用户登录
 *
 * 【使用场景】
 *   用户在登录页面输入邮箱和密码后点击"登录"，
 *   前端将表单数据封装为本 DTO 提交到后端进行认证。
 */
@Data
public class LoginDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录邮箱（必填）
     *
     * 用户注册时使用的邮箱地址，作为登录账号。
     * 必须符合邮箱格式（xxx@xxx.xxx）。
     */
    @NotBlank
    @Email
    private String email;

    /**
     * 登录密码（必填）
     *
     * 用户的明文密码。后端会将其与数据库中存储的密码哈希进行比对验证。
     * 注意：传输过程中应通过 HTTPS 加密，后端不会保存明文密码。
     */
    @NotBlank
    private String password;
}
