package com.workflowx.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CreateUserRequest Bean Validation 规则测试（P2-03）。
 * 覆盖任务要求的 username/email/password/nickname 全部格式边界。
 */
class CreateUserRequestValidationTest {

    private static final String VALID_PASSWORD = "Password@123";
    private static final String VALID_EMAIL = "user@test.local";

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private boolean isValid(CreateUserRequest request) {
        return validator.validate(request).isEmpty();
    }

    private CreateUserRequest base(String username, String email, String password) {
        return new CreateUserRequest(username, email, password, "nick");
    }

    @Test
    void usernameLengthBoundaries() {
        assertTrue(!isValid(base("ab", VALID_EMAIL, VALID_PASSWORD)), "2 字符应失败");
        assertTrue(isValid(base("abc", VALID_EMAIL, VALID_PASSWORD)), "3 字符应通过");
        assertTrue(isValid(base("a".repeat(32), VALID_EMAIL, VALID_PASSWORD)), "32 字符应通过");
        assertTrue(!isValid(base("a".repeat(33), VALID_EMAIL, VALID_PASSWORD)), "33 字符应失败");
    }

    @Test
    void usernameCharsetRules() {
        assertTrue(!isValid(base("用户名测试", VALID_EMAIL, VALID_PASSWORD)), "中文应失败");
        assertTrue(!isValid(base("user-name!", VALID_EMAIL, VALID_PASSWORD)), "特殊字符应失败");
        assertTrue(!isValid(base("user name", VALID_EMAIL, VALID_PASSWORD)), "空格应失败");
        assertTrue(!isValid(base(null, VALID_EMAIL, VALID_PASSWORD)), "空值应失败");
        assertTrue(!isValid(base("", VALID_EMAIL, VALID_PASSWORD)), "空串应失败");
    }

    @Test
    void emailRules() {
        assertTrue(isValid(base("u1v", VALID_EMAIL, VALID_PASSWORD)), "合法邮箱应通过");
        assertTrue(!isValid(base("u2v", "not-an-email", VALID_PASSWORD)), "非法邮箱应失败");
        assertTrue(!isValid(base("u3v", null, VALID_PASSWORD)), "空邮箱应失败");
        assertTrue(!isValid(base("u4v", "", VALID_PASSWORD)), "空串邮箱应失败");
        // 边界: 邮箱总长 100（@Email 另约束 local part ≤64，故 local=64, domain=35）；再长 1 位即超 100
        assertTrue(isValid(base("u5v", "a".repeat(64) + "@" + "b".repeat(29) + ".local", VALID_PASSWORD)), "100 字符邮箱应通过");
        assertTrue(!isValid(base("u6v", "a".repeat(64) + "@" + "b".repeat(30) + ".local", VALID_PASSWORD)), "101 字符邮箱应失败");
    }

    @Test
    void passwordRules() {
        assertTrue(isValid(base("u7v", VALID_EMAIL, "Abcdef1g")), "8 位含字母数字应通过");
        assertTrue(!isValid(base("u8v", VALID_EMAIL, "Abcdef1")), "7 位应失败");
        assertTrue(!isValid(base("u9v", VALID_EMAIL, "Abcdefgh")), "纯字母应失败");
        assertTrue(!isValid(base("u10v", VALID_EMAIL, "12345678")), "纯数字应失败");
        assertTrue(!isValid(base("u11v", VALID_EMAIL, "")), "空密码应失败");
        assertTrue(!isValid(base("u12v", VALID_EMAIL, null)), "null 密码应失败");
        assertTrue(isValid(base("u13v", VALID_EMAIL, "a".repeat(31) + "1" + "b".repeat(32))), "64 位应通过");
        assertTrue(!isValid(base("u14v", VALID_EMAIL, "a".repeat(31) + "1" + "b".repeat(33))), "65 位应失败");
        assertTrue(!isValid(base("u15v", VALID_EMAIL, "Abcdef1 g")), "含空格应失败");
    }

    @Test
    void nicknameRules() {
        assertTrue(isValid(new CreateUserRequest("u16", VALID_EMAIL, VALID_PASSWORD, "n".repeat(50))), "50 字符昵称应通过");
        assertTrue(!isValid(new CreateUserRequest("u17", VALID_EMAIL, VALID_PASSWORD, "n".repeat(51))), "51 字符昵称应失败");
        assertTrue(isValid(new CreateUserRequest("u18", VALID_EMAIL, VALID_PASSWORD, null)), "昵称可空");
    }

    @Test
    void fullValidRequestShouldPass() {
        assertTrue(isValid(base("p2_valid_user", VALID_EMAIL, VALID_PASSWORD)));
    }
}
