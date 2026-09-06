package com.workflowx.auth;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis 故障 fail-closed 测试（P2-13）。
 * Redis 不可用时: 登录必须失败（统一服务错误），不得因计数器失败而放行，
 * 也不得向客户端泄漏 Redis 内部异常细节。Mock 仅用于模拟基础设施故障，不代表业务行为。
 */
@SpringBootTest(properties = "jwt.secret=test-only-secret-for-redis-failclosed-test-workflowx-0123456789")
@AutoConfigureMockMvc
class AuthRedisFailClosedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void loginShouldFailClosedWhenRedisUnavailable() throws Exception {
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(Mockito.anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down (simulated)"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"Admin@123456\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("internal server error"));
    }
}
