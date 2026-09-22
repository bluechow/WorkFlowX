package com.workflowx.common.storage;

import com.workflowx.common.exception.ResourceNotFoundException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 对象存储实现（Phase 8; ADR-018）。
 * 错误语义: 对象不存在 → 404 语义（ResourceNotFoundException）；其余 IO 失败原样抛出由全局兜底 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public void upload(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("对象上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredObject download(String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
            return new StoredObject(stream, stat.size(), stat.contentType());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new ResourceNotFoundException("attachment object", objectKey);
            }
            throw new StorageException("对象读取失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageException("对象读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageException("对象删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageException("对象查询失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageException("对象查询失败: " + e.getMessage(), e);
        }
    }

    /** 存储层技术故障（区别于业务 4xx）：由全局兜底转 500，message 不暴露 MinIO 细节给客户端。 */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
