package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.MediaAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 媒体资源 Mapper —— 操作数据库表 `media_asset`
 *
 * 该接口负责对"媒体资源"表进行数据库操作。
 * 媒体资源指用户上传的图片、视频、音频等文件。每条记录存储了
 * 文件的存储路径（如 OSS/S3 的 URL）、文件类型、文件大小、
 * 所属用户等信息。媒体资源通过关联表与内容进行绑定。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条媒体资源记录（上传文件后保存元信息）
 *     - deleteById(id)            根据主键删除媒体资源记录
 *     - updateById(entity)        根据主键更新媒体资源信息（如更新文件路径）
 *     - selectById(id)            根据主键查询单个媒体资源
 *     - selectList(wrapper)       按条件查询媒体资源列表（如按类型筛选）
 *     - selectPage(page, wrapper) 分页查询媒体资源列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按文件类型统计数量或按用户查询资源总大小，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {
}
