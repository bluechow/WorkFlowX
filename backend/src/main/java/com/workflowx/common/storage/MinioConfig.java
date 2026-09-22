package com.workflowx.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配（Phase 8; ADR-018）。
 * 启动时 fail-fast 校验 bucket 可达（Master Prompt §24 容器健康检查之外的的应用级确认）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("MinIO bucket '{}' 不存在，已自动创建", properties.getBucket());
            }
            log.info("MinIO 连接就绪: endpoint={}, bucket={}", properties.getEndpoint(), properties.getBucket());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 连接失败（endpoint=" + properties.getEndpoint() + "）: " + e.getMessage(), e);
        }
        return client;
    }
}
