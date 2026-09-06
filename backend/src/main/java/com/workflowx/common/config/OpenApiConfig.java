package com.workflowx.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（ADR-005 / 任务 1-5）。
 * dev 环境启用 swagger-ui；prod 默认关闭（见 application-prod.yml）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workflowxOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("WorkFlowX API")
                .description("WorkFlowX 企业级项目协作与工单管理平台 API。"
                        + "统一前缀 /api/v1，统一响应结构（code/message/data/timestamp/traceId），"
                        + "详细约定见 docs/api/api-conventions.md。")
                .version("v1"));
    }
}
