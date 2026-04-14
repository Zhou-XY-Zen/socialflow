package com.socialflow.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求 DTO —— 用于新用户注册的接口入参
 *
 * 【作用】封装用户注册时需要填写的信息（邮箱、密码、昵称）。
 *   后端接收后会检查邮箱是否已被注册，对密码进行哈希加密后存入数据库。
 *
 * 【对应 API 接口】
 *   POST /api/auth/register  —— 用户注册
 *
 * 【使用场景】
 *   新用户在注册页面填写邮箱、密码和昵称后点击"注册"，
 *   前端将表单数据封装为本 DTO 提交到后端。
 *   注册成功后通常自动登录并返回 Token。
 */
@Data
public class RegisterDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 注册邮箱（必填）
     *
     * 用户的邮箱地址，将作为登录账号使用，全局唯一。
     * 必须符合邮箱格式（xxx@xxx.xxx）。
     * 如果该邮箱已被注册，接口会返回错误提示。
     */
    @NotBlank
    @Email
    private String email;

    /**
     * 注册密码（必填）
     *
     * 用户设置的登录密码。
     * 长度限制：8 ~ 64 个字符。
     * 后端会使用 BCrypt 等算法对密码进行哈希加密后存储，不保存明文。
     */
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    /**
     * 用户昵称（必填）
     *
     * 用户的显示名称，在系统中展示使用。
     * 长度限制：最多 64 个字符。
     * 不要求唯一，多个用户可以使用相同昵称。
     */
    @NotBlank
    @Size(max = 64)
    private String nickname;
}
