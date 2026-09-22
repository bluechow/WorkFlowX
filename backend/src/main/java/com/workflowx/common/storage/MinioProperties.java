package com.workflowx.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO 连接配置（Phase 8; ADR-018）。复用 compose.yaml 的 workflowx bucket，不建第二套存储。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;
}
