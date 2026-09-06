package com.workflowx.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI Security 文档测试（P2-14）。
 * 验证: Bearer SecurityScheme 存在、受保护端点声明 security requirement、
 * Swagger 资源公开但不影响业务 API 鉴权。
 */
@SpringBootTest(properties = "jwt.secret=test-only-secret-for-openapi-security-test-workflowx-0123456789")
@AutoConfigureMockMvc
class OpenApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsShouldExposeBearerJwtSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void securityRequirementSemanticsShouldMatchRuntimeAuth() throws Exception {
        // P2-14 修正: 文档安全声明必须与运行时鉴权一一对应——
        // 公开端点(login/health)显式空 security；受保护端点(logout/me/users)显式 bearerAuth
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 根级 requirement 保留
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                // 公开: login / health 为空 security
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isEmpty())
                // 受保护: logout / me / users 显式 bearerAuth
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/status'].patch.security[0].bearerAuth").exists());
    }

    @Test
    void generatedSchemasMustNotExposePasswordHash() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(!body.contains("passwordHash"), "OpenAPI schema 不得暴露 passwordHash 字段: " + body.contains("passwordHash"));
    }

    @Test
    void swaggerUiShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void swaggerVisibilityMustNotWeakenBusinessApiAuth() throws Exception {
        // Swagger 资源公开 ≠ 业务 API 公开
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/{id}", 1L)).andExpect(status().isUnauthorized());
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
