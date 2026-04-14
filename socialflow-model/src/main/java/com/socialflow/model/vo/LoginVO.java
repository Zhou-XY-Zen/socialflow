package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录结果视图对象（VO）—— 用于向前端返回登录成功后的认证信息
 *
 * 【作用】封装用户登录成功后返回的全部信息：
 *   JWT Token（用于后续接口的身份认证）、Token 有效期和用户基本信息。
 *   前端收到后将 Token 保存到本地存储，后续请求在 Header 中携带。
 *
 * 【对应 API 接口（作为返回值）】
 *   - POST /api/auth/login    —— 用户登录成功后返回
 *   - POST /api/auth/register —— 用户注册成功后自动登录也返回本对象
 *
 * 【使用场景】
 *   用户登录或注册成功后，前端接收本对象：
 *   1. 将 token 保存到 localStorage 或 Cookie
 *   2. 后续所有 API 请求在 Authorization Header 中携带 "Bearer {token}"
 *   3. 根据 expiresIn 设置 Token 自动刷新或过期提示
 *   4. 使用 user 信息展示用户资料
 */
@Data
public class LoginVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * JWT 认证令牌
     *
     * 登录成功后由服务端签发的 JSON Web Token。
     * 前端需要在后续所有需要认证的 API 请求的 Header 中携带：
     * Authorization: Bearer {token}
     */
    private String token;

    /**
     * Token 有效期（秒）
     *
     * 该 Token 从签发起多少秒后过期。
     * 示例：86400 表示 24 小时后过期。
     * 前端可以据此计算过期时间，提前提示用户重新登录或自动刷新。
     */
    private Long expiresIn;

    /**
     * 当前登录用户的基本信息
     *
     * 嵌套的 UserVO 对象，包含用户 ID、邮箱、昵称、头像等信息。
     * 前端可以直接使用，无需再额外调用"获取用户信息"接口。
     */
    private UserVO user;
}
