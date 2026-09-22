package com.workflowx.issue.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 附件策略配置（Phase 8; ADR-018）。 */
@Data
@Component
@ConfigurationProperties(prefix = "attachment")
public class AttachmentProperties {

    /** 单文件大小上限（字节），默认 10MB */
    private long maxSizeBytes = 10 * 1024 * 1024L;
}
