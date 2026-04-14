package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体类 —— 对应数据库表 `sys_user`
 *
 * 【作用】存储系统中所有注册用户的基本信息，包括登录凭证（邮箱+密码哈希）和个人资料（昵称、头像）。
 *   这是整个系统的用户主表，几乎所有业务表都会通过 userId 关联到本表。
 *
 * 【为什么需要它】
 *   用户是系统的核心主体，所有操作（生成文案、管理知识库、发布内容等）都需要归属到某个用户。
 *   本表记录用户的身份信息和账号状态。
 *
 * 【关联关系】
 *   - 其他表的 user_id 字段均关联到本表的 id
 *   - user_api_key 表存储该用户配置的 AI API 密钥
 *   - user_preference 表存储该用户的个性化偏好设置
 *   - content 表存储该用户创建的所有文案
 *
 * 【使用场景】
 *   - 用户注册时创建一条记录
 *   - 用户登录时根据 email 查询并校验密码
 *   - 展示用户信息时查询昵称、头像等
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /**
     * 用户邮箱地址
     *
     * 作为登录账号使用，全局唯一。格式示例：user@example.com
     */
    private String email;

    /**
     * 密码哈希值
     *
     * 存储的是经过 BCrypt 等算法加密后的密码，而非明文密码。
     * 用户登录时，将输入的密码进行同样的哈希运算后与本字段比对。
     */
    private String passwordHash;

    /**
     * 用户昵称
     *
     * 用于页面展示的用户名称，可以由用户自定义，不要求唯一。
     */
    private String nickname;

    /**
     * 用户头像 URL
     *
     * 存储头像图片的访问地址，可以是本系统的 OSS 地址或第三方图片链接。
     * 可为空，为空时前端显示默认头像。
     */
    private String avatarUrl;

    /**
     * 账号状态
     *
     * 可选值：0 = 禁用（该用户无法登录），1 = 正常（默认值）。
     * 管理员可以通过修改此字段来禁用或启用某个用户。
     */
    private Integer status;
}
