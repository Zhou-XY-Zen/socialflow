package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户信息视图对象（VO）—— 用于向前端返回用户的基本信息
 *
 * 【作用】封装用户的可公开展示信息，剔除了密码哈希等敏感字段。
 *   是前端展示用户资料时使用的标准数据结构。
 *
 * 【与 SysUser 实体的区别】
 *   SysUser 包含 passwordHash 等敏感字段，不能直接返回给前端。
 *   UserVO 只包含可以安全展示的字段。
 *
 * 【对应 API 接口（作为返回值）】
 *   - GET /api/user/me          —— 获取当前登录用户信息
 *   - 作为 LoginVO.user 的嵌套对象，在登录接口中返回
 *
 * 【使用场景】
 *   - 登录成功后返回用户基本信息
 *   - 页面顶部导航栏展示用户昵称和头像
 *   - 个人中心页面展示用户资料
 */
@Data
public class UserVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     *
     * 用户的唯一标识。
     */
    private Long id;

    /**
     * 用户邮箱
     *
     * 用户的注册邮箱，也是登录账号。
     */
    private String email;

    /**
     * 用户昵称
     *
     * 用户设置的显示名称。
     */
    private String nickname;

    /**
     * 用户头像 URL
     *
     * 头像图片的访问地址。为空时前端显示默认头像。
     */
    private String avatarUrl;

    /**
     * 账号状态
     *
     * 0 = 禁用，1 = 正常。
     */
    private Integer status;
}
