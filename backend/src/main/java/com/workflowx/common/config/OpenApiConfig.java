package com.workflowx.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（P2-05 建立，P2-14 增加 Bearer Security Scheme）。
 * dev 环境启用 swagger-ui；prod 默认关闭（见 application-prod.yml）。
 * Swagger 资源公开 ≠ 业务 API 公开：鉴权仍由 SecurityFilterChain 强制（SecurityConfig）。
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI workflowxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WorkFlowX API")
                        .description("WorkFlowX 企业级项目协作与工单管理平台 API。"
                                + "统一前缀 /api/v1，统一响应结构（code/message/data/timestamp/traceId），"
                                + "详细约定见 docs/api/api-conventions.md。"
                                + "认证: 经 POST /api/v1/auth/login 获取 JWT 后通过 Authorize 携带 Bearer Token；"
                                + "未认证 401（authentication required），权限不足 403（permission denied）。")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
