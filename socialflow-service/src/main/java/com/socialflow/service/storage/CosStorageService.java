package com.socialflow.service.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 腾讯云 COS 对象存储服务实现。
 *
 * 基于腾讯云 COS Java SDK，提供文件上传、删除、URL 获取等操作。
 * 图片通过 COS 公有读域名直接访问，适合需要 CDN 加速的生产场景。
 */
@Slf4j
@Service
public class CosStorageService implements StorageService {

    @Value("${socialflow.storage.cos-secret-id}")
    private String secretId;

    @Value("${socialflow.storage.cos-secret-key}")
    private String secretKey;

    @Value("${socialflow.storage.cos-region}")
    private String region;

    @Value("${socialflow.storage.cos-bucket}")
    private String bucket;

    @Value("${socialflow.storage.public-url}")
    private String publicUrl;

    /** 腾讯云 COS 客户端 */
    private COSClient cosClient;

    @PostConstruct
    public void init() {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        this.cosClient = new COSClient(credentials, clientConfig);
        log.info("腾讯云 COS 存储服务初始化完成: bucket={}, region={}", bucket, region);
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("COS 客户端已关闭");
        }
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType);

            PutObjectRequest request = new PutObjectRequest(bucket, objectKey, inputStream, metadata);
            cosClient.putObject(request);

            log.debug("COS 上传成功: objectKey={}, size={}KB", objectKey, size / 1024);
        } catch (Exception e) {
            log.error("COS 上传失败: objectKey={}", objectKey, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void upload(String objectKey, byte[] data, String contentType) {
        upload(objectKey, new ByteArrayInputStream(data), data.length, contentType);
    }

    @Override
    public void delete(String objectKey) {
        try {
            cosClient.deleteObject(bucket, objectKey);
            log.debug("COS 删除成功: objectKey={}", objectKey);
        } catch (Exception e) {
            log.error("COS 删除失败: objectKey={}", objectKey, e);
        }
    }

    @Override
    public String getPublicUrl(String objectKey) {
        // 拼接公有读 URL：https://bucket.cos.region.myqcloud.com/objectKey
        String base = publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        return base + objectKey;
    }
}
