package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.PlatformAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台账号 Mapper —— 操作数据库表 `platform_account`
 *
 * 该接口负责对"平台账号"表进行数据库操作。
 * 平台账号记录了用户在各个社交媒体平台（如微信公众号、微博、抖音、
 * Twitter 等）上绑定的账号信息，包括平台类型、账号名称、授权令牌（Token）、
 * 授权过期时间等。系统在发布内容时会读取此表获取对应平台的授权信息。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条平台账号记录（绑定新平台）
 *     - deleteById(id)            根据主键删除平台账号（解绑平台）
 *     - updateById(entity)        根据主键更新账号信息（如刷新 Token）
 *     - selectById(id)            根据主键查询单个平台账号
 *     - selectList(wrapper)       按条件查询平台账号列表（如查某用户的所有平台）
 *     - selectPage(page, wrapper) 分页查询平台账号列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按平台类型批量查询或统计各平台的账号数，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface PlatformAccountMapper extends BaseMapper<PlatformAccount> {
}
