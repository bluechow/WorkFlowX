package com.workflowx.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子密码哈希匹配测试（P2-01 DoD）：
 * 正确密码必须匹配、错误密码必须不匹配——用 Spring 官方 BCryptPasswordEncoder 实测，而非仅看格式。
 * 说明：测试代码中的明文密码仅用于验证 dev 种子凭据（仅 DEV 环境有效），不属于业务源码。
 */
@SpringBootTest
class SeedPasswordHashTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String hashOf(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?", String.class, username);
    }

    @Test
    void adminCorrectPasswordShouldMatch() {
        assertTrue(encoder.matches("Admin@123456", hashOf("admin")));
    }

    @Test
    void adminWrongPasswordShouldNotMatch() {
        assertFalse(encoder.matches("Admin@123456x", hashOf("admin")));
        assertFalse(encoder.matches("wrong-password", hashOf("admin")));
        assertFalse(encoder.matches("", hashOf("admin")));
    }

    @Test
    void user1CorrectPasswordShouldMatch() {
        assertTrue(encoder.matches("Member@123456", hashOf("user1")));
    }

    @Test
    void user1WrongPasswordShouldNotMatch() {
        assertFalse(encoder.matches("Member@123456x", hashOf("user1")));
        assertFalse(encoder.matches("wrong-password", hashOf("user1")));
        assertFalse(encoder.matches("Admin@123456", hashOf("user1")));
    }
}
