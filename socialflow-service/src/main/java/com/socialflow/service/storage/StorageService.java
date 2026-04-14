package com.socialflow.service.storage;

import java.io.InputStream;

/**
 * 统一对象存储服务接口。
 *
 * 抽象出上传、删除、获取 URL 等操作，
 * 底层实现可以是腾讯云 COS、MinIO、阿里云 OSS 等。
 */
public interface StorageService {

    /**
     * 上传文件到对象存储。
     *
     * @param objectKey   对象键（如 media/123/uuid_file.jpg）
     * @param inputStream 文件输入流
     * @param size        文件大小（字节）
     * @param contentType MIME 类型（如 image/jpeg）
     */
    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 上传字节数组到对象存储。
     *
     * @param objectKey   对象键
     * @param data        文件字节数据
     * @param contentType MIME 类型
     */
    void upload(String objectKey, byte[] data, String contentType);

    /**
     * 删除对象。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);

    /**
     * 获取对象的公开访问 URL。
     *
     * @param objectKey 对象键
     * @return 可公开访问的 URL
     */
    String getPublicUrl(String objectKey);
}
