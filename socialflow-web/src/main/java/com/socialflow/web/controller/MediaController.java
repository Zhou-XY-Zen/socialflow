package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.PageResult;
import com.socialflow.common.result.R;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.service.media.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体素材控制器 —— 管理用户上传的图片、视频等媒体资源。
 *
 * 本控制器处理的基础 URL 路径为 {@code /api/v1/media}，提供以下功能：
 *     - 上传媒体文件（图片、视频）到 对象存储 对象存储
 *     - 分页浏览素材列表，支持按类型筛选和关键词搜索
 *     - 删除素材（同时删除 对象存储 中的文件和数据库记录）
 *
 * 所有接口都需要用户登录后才能访问，通过 Sa-Token 的 {@code StpUtil.getLoginIdAsLong()}
 * 获取当前登录用户的 ID，确保用户只能操作自己的素材。
 *
 * @see MediaService 媒体素材业务逻辑的具体实现
 */
@Tag(name = "media", description = "media asset management")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/media")
@RequiredArgsConstructor
public class MediaController {

    /** 媒体素材服务，封装了上传、查询、删除等业务逻辑 */
    private final MediaService mediaService;

    /**
     * 上传媒体文件。
     *
     * 接口路径：POST /api/v1/media/upload
     *
     * 功能：将用户上传的图片或视频文件存储到 对象存储，同时在数据库中创建元信息记录。
     * 前端需要以 multipart/form-data 格式提交文件。
     *
     * @param file 上传的媒体文件（通过 multipart 表单上传）
     * @param tags 素材标签（可选，逗号分隔），用于分类和搜索
     * @return 统一响应体 R，包含新创建的媒体资产信息 MediaAsset
     */
    @Operation(summary = "upload media file")
    @PostMapping("/upload")
    public R<MediaAsset> upload(@RequestPart("file") MultipartFile file,
                                @RequestParam(required = false) String tags) {
        return R.ok(mediaService.upload(StpUtil.getLoginIdAsLong(), file, tags));
    }

    /**
     * 分页查询媒体素材列表。
     *
     * 接口路径：GET /api/v1/media/list
     *
     * 功能：获取当前用户的素材列表，支持分页、按类型筛选和关键词搜索。
     *
     * @param pageNum  页码，从 1 开始，默认第 1 页
     * @param pageSize 每页显示条数，默认 12 条
     * @param fileType 文件类型筛选（可选，IMAGE 或 VIDEO）
     * @param keyword  关键词搜索（可选，匹配文件名和标签）
     * @return 统一响应体 R，包含分页结果 PageResult
     */
    @Operation(summary = "paged media asset list")
    @GetMapping("/list")
    public R<PageResult<MediaAsset>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "12") Integer pageSize,
                                          @RequestParam(required = false) String fileType,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(mediaService.list(StpUtil.getLoginIdAsLong(),
                pageNum, pageSize, fileType, keyword));
    }

    /**
     * 删除媒体素材。
     *
     * 接口路径：DELETE /api/v1/media/{id}
     *
     * 功能：删除指定的媒体素材，同时从 对象存储 中移除实际文件。
     * 只有素材的所有者才能执行删除操作。
     *
     * @param id 要删除的素材 ID（从 URL 路径中获取）
     * @return 统一响应体 R，无数据体
     */
    @Operation(summary = "delete media asset")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mediaService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }
}
