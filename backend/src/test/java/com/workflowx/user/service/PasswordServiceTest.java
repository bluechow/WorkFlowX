package com.workflowx.user.service;

import com.workflowx.user.service.impl.PasswordServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PasswordService 单元测试（P2-04）。
 * 直连真实 BCryptPasswordEncoder(10)，验证哈希行为而非格式表象（Master Prompt §2.5）。
 */
class PasswordServiceTest {

    private final PasswordService passwordService =
            new PasswordServiceImpl(new BCryptPasswordEncoder(10));

    private static final String RAW = "Password@123";

    @Test
    void encodeShouldNotReturnPlainPassword() {
        String hash = passwordService.encode(RAW);
        assertNotEquals(RAW, hash);
        assertTrue(!hash.contains(RAW), "哈希不得包含明文片段");
    }

    @Test
    void encodeShouldProduceBcryptStrength10Format() {
        String hash = passwordService.encode(RAW);
        assertTrue(hash.matches("^\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$"), "应为 BCrypt(10) 格式: " + hash);
        assertEquals(60, hash.length());
    }

    @Test
    void matchesShouldReturnTrueForCorrectPassword() {
        assertTrue(passwordService.matches(RAW, passwordService.encode(RAW)));
    }

    @Test
    void matchesShouldReturnFalseForWrongPassword() {
        String hash = passwordService.encode(RAW);
        assertTrue(!passwordService.matches("Password@124", hash));
        assertTrue(!passwordService.matches("password@123", hash));
        assertTrue(!passwordService.matches("", hash));
    }

    @Test
    void samePlainPasswordShouldProduceDifferentHashes() {
        String hash1 = passwordService.encode(RAW);
        String hash2 = passwordService.encode(RAW);
        assertNotEquals(hash1, hash2, "BCrypt 随机盐应使同明文产生不同哈希");
        assertTrue(passwordService.matches(RAW, hash1));
        assertTrue(passwordService.matches(RAW, hash2));
    }

    @Test
    void encodedHashShouldRemainVerifiable() {
        String hash = passwordService.encode("Abcdef1g");
        assertTrue(passwordService.matches("Abcdef1g", hash));
        assertTrue(!passwordService.matches("Abcdef2g", hash));
    }
}
