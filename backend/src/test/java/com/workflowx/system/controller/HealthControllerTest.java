package com.workflowx.system.controller;

import com.workflowx.common.security.JwtServiceImpl;
import com.workflowx.common.security.RestAccessDeniedHandler;
import com.workflowx.common.security.RestAuthenticationEntryPoint;
import com.workflowx.common.security.SecurityConfig;
import com.workflowx.common.trace.TraceIdFilter;
import com.workflowx.system.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查接口测试（任务 1-7.1）。
 * 验证统一响应结构（ADR-005）与 traceId 复用/回写机制，而不只是 HTTP 200（Master Prompt §18）。
 * P2-05 起导入真实 SecurityFilterChain——health 为公开端点，在真实安全链路下必须仍然可达。
 */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtServiceImpl.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnUnifiedStructure() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("workflowx-backend"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())));
    }

    @Test
    void healthShouldReuseUpstreamTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(TraceIdFilter.TRACE_ID_HEADER, "trace-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(header().string("X-Trace-Id", "trace-123"));
    }
}
