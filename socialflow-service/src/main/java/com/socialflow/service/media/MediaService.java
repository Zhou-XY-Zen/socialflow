package com.socialflow.service.media;

import com.socialflow.common.result.PageResult;
import com.socialflow.model.entity.MediaAsset;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体素材管理服务接口 —— 管理用户上传的图片、视频等媒体资源。
 *
 * 负责的业务领域：媒体文件的上传、浏览、检索和删除。
 * 媒体素材是内容发布的重要组成部分，用户可以在生成文案后附加图片或视频一起发布。
 *
 * 主要功能：
 *     - 上传 —— 支持图片、视频等多种格式的文件上传，并自动提取元信息
 *     - 管理 —— 分页浏览、按类型筛选、关键词搜索
 *     - 语义搜索 —— 基于 AI 生成的媒体描述进行语义级别的检索
 *
 * 对应的 Controller：{@code MediaController}，路由前缀为 {@code /api/v1/media/*}。
 */
public interface MediaService {

    /**
     * 上传一个媒体文件。
     *
     * 文件上传后，系统会自动提取文件类型、尺寸等元信息，
     * 并将文件存储到对象存储服务（腾讯云 COS）。
     *
     * @param userId 当前登录用户的 ID
     * @param file   上传的文件（Spring 的 MultipartFile 对象）
     * @param tags   用户为素材添加的标签（逗号分隔），便于后续检索
     * @return 新创建的媒体资产实体，包含文件 URL、类型、大小等信息
     */
    MediaAsset upload(Long userId, MultipartFile file, String tags);

    /**
     * 分页查询媒体素材列表（支持筛选）。
     *
     * @param userId   当前登录用户的 ID（只能查看自己的素材）
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量
     * @param fileType 文件类型筛选条件（可选，如 "image"、"video"）
     * @param keyword  关键词搜索（可选，匹配文件名和标签）
     * @return 分页结果，包含当前页的素材列表和总记录数
     */
    PageResult<MediaAsset> list(Long userId, Integer pageNum, Integer pageSize,
                                 String fileType, String keyword);

    /**
     * 删除一个媒体素材。
     *
     * 同时删除数据库中的记录和对象存储中的实际文件。
     *
     * @param userId 当前登录用户的 ID（用于权限校验）
     * @param id     要删除的媒体素材 ID
     */
    void delete(Long userId, Long id);

    /**
     * 基于语义的媒体素材搜索。
     *
     * 与普通关键词搜索不同，语义搜索可以理解查询的含义。
     * 例如搜索"海边日落"，也能找到描述为"夕阳下的沙滩"的图片。
     * 底层通过 AI 对媒体描述进行向量化，然后在向量数据库中做相似度检索。
     *
     * @param userId 当前登录用户的 ID
     * @param query  自然语言查询文本（如 "产品特写照片"）
     * @param topK   返回最相关的前 K 条结果
     * @return 匹配的媒体素材分页结果，按相似度降序排列
     */
    PageResult<MediaAsset> semanticSearch(Long userId, String query, int topK);
}
