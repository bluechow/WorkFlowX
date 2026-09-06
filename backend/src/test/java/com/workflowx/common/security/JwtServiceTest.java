package com.workflowx.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtService 单元测试（P2-06）：专用测试密钥（不复用任何环境配置）。
 * 覆盖: 签发/解析/验证/篡改/错误密钥/过期/malformed/alg=none/缺失 claims/敏感信息排除。
 */
class JwtServiceTest {

    /** 仅本测试使用的专用密钥（≥32 字节），不代表任何环境配置 */
    private static final String TEST_SECRET_A = "test-only-secret-a-for-workflowx-p2-jwt-0123456789abcdef";
    private static final String TEST_SECRET_B = "test-only-secret-b-for-workflowx-p2-jwt-9876543210fedcba";
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    private JwtServiceImpl service;

    private JwtServiceImpl serviceOf(String secret, int expireHours) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpireHours(expireHours);
        props.setIssuer("workflowx-test");
        return new JwtServiceImpl(props);
    }

    @BeforeEach
    void setUp() {
        service = serviceOf(TEST_SECRET_A, 2);
    }

    @Test
    void generateAndParseRoundTripShouldCarryAllClaims() {
        String token = service.generateToken(42L, "alice", List.of("ADMIN", "MEMBER"), NOW, NOW.plusSeconds(7200));
        JwtPayload payload = service.parseToken(token);

        assertEquals(42L, payload.userId());
        assertEquals("alice", payload.username());
        assertEquals(List.of("ADMIN", "MEMBER"), payload.roles());
        assertNotNull(payload.jti());
        assertTrue(!payload.jti().isBlank(), "jti 必须存在且非空");
        assertEquals(NOW, payload.issuedAt());
        assertEquals(NOW.plusSeconds(7200), payload.expiresAt());
    }

    @Test
    void defaultGenerateShouldUseConfiguredTtl() {
        String token = service.generateToken(1L, "bob", List.of("MEMBER"));
        JwtPayload payload = service.parseToken(token);
        long ttlSeconds = payload.expiresAt().getEpochSecond() - payload.issuedAt().getEpochSecond();
        assertEquals(7200L, ttlSeconds, "默认有效期应为 2 小时");
    }

    @Test
    void validTokenShouldValidateTrue() {
        assertTrue(service.validateToken(service.generateToken(1L, "bob", List.of("MEMBER"))));
    }

    @Test
    void wrongSecretShouldFailValidation() {
        String token = service.generateToken(1L, "bob", List.of("MEMBER"));
        JwtServiceImpl other = serviceOf(TEST_SECRET_B, 2);
        assertTrue(!other.validateToken(token), "不同密钥签发的 token 必须验证失败");
        assertThrows(JwtException.class, () -> other.parseToken(token));
    }

    @Test
    void tamperedPayloadShouldFailValidation() {
        String token = service.generateToken(1L, "alice", List.of("MEMBER"));
        String[] parts = token.split("\\.");
        // 解码 payload，修改 username 后重新编码（不重签签名）
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payloadJson.replace("alice", "mallory");
        String forged = parts[0] + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.getBytes(StandardCharsets.UTF_8)) +
                "." + parts[2];
        assertTrue(!service.validateToken(forged), "篡改 payload 必须验证失败");
        assertThrows(JwtException.class, () -> service.parseToken(forged));
    }

    @Test
    void modifiedSignatureShouldFailValidation() {
        String token = service.generateToken(1L, "alice", List.of("MEMBER"));
        String[] parts = token.split("\\.");
        String badSignature = ("x" + parts[2].substring(1));
        if (badSignature.equals(parts[2])) {
            badSignature = "y" + parts[2].substring(1);
        }
        String forged = parts[0] + "." + parts[1] + "." + badSignature;
        assertTrue(!service.validateToken(forged), "修改签名必须验证失败");
    }

    @Test
    void expiredTokenShouldFailValidation() {
        String expired = service.generateToken(1L, "alice", List.of("MEMBER"), NOW.minusSeconds(7200), NOW.minusSeconds(60));
        assertTrue(!service.validateToken(expired), "过期 token 必须验证失败");
        assertThrows(JwtException.class, () -> service.parseToken(expired));
    }

    @Test
    void malformedTokenShouldFailValidation() {
        assertTrue(!service.validateToken("not-a-jwt"));
        assertTrue(!service.validateToken("a.b.c"));
        assertTrue(!service.validateToken(""));
        assertThrows(JwtException.class, () -> service.parseToken("not-a-jwt"));
    }

    @Test
    void algNoneUnsignedTokenShouldFailValidation() {
        // 未签名（无 signWith）token：parser 固定 verifyWith(密钥)，alg=none 天然被拒绝
        String unsigned = Jwts.builder()
                .subject("1")
                .claim("username", "alice")
                .claim("roles", List.of("ADMIN"))
                .issuer("workflowx-test")
                .compact();
        assertThrows(JwtException.class, () -> service.parseToken(unsigned));
        assertTrue(!service.validateToken(unsigned));
    }

    @Test
    void missingRequiredClaimsShouldFail() {
        // 与实现同密钥手工签发"缺少 username/roles claims"的 token
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET_A.getBytes(StandardCharsets.UTF_8));
        String incomplete = Jwts.builder()
                .id("jti-x")
                .subject("1")
                .issuer("workflowx-test")
                .issuedAt(java.util.Date.from(NOW))
                .expiration(java.util.Date.from(NOW.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        assertThrows(JwtException.class, () -> service.parseToken(incomplete), "缺失必要 claims 必须失败");
        assertTrue(!service.validateToken(incomplete));
    }

    @Test
    void tokenPayloadMustNotContainSensitiveInformation() {
        String token = service.generateToken(1L, "alice", List.of("MEMBER"));
        String payloadJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(!payloadJson.toLowerCase().contains("password"), "JWT 不得包含 password");
        assertTrue(!payloadJson.toLowerCase().contains("password_hash"), "JWT 不得包含 password_hash");
        assertTrue(!payloadJson.toLowerCase().contains("email"), "JWT 不得包含 email");
    }

    @Test
    void weakSecretShouldFailFastOnTokenGeneration() {
        // 16 字节密钥 < HS256 要求的 32 字节: 生成 token 时必须立即失败（fail-fast，禁止弱密钥上线）
        JwtServiceImpl weak = serviceOf("too-short-secret", 2);
        org.junit.jupiter.api.Assertions.assertThrows(
                io.jsonwebtoken.security.WeakKeyException.class,
                () -> weak.generateToken(1L, "alice", List.of("MEMBER")));
    }

    @Test
    void sameUserDifferentTokensShouldHaveDifferentJti() {
        String t1 = service.generateToken(1L, "alice", List.of("MEMBER"));
        String t2 = service.generateToken(1L, "alice", List.of("MEMBER"));
        assertNotEquals(service.parseToken(t1).jti(), service.parseToken(t2).jti(), "jti 必须每次唯一");
        assertNotEquals(t1, t2);
    }
}
