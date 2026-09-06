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
    void securityRequirementSemanticsShouldBeCorrect() throws Exception {
        // 根级 security requirement 对所有未覆盖操作生效（受保护 API 继承）；
        // health 端点以空 @SecurityRequirements 覆盖，标记为公开
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/users'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security").doesNotExist());
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
