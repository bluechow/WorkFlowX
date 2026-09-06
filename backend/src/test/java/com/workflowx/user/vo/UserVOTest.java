package com.workflowx.user.vo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UserVO 安全约束测试（P2-01）：对外模型与实体隔离，禁止暴露任何密码字段。 */
class UserVOTest {

    @Test
    void voMustNotContainAnyPasswordField() {
        Set<String> names = Arrays.stream(UserVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        boolean containsPassword = names.stream()
                .anyMatch(n -> n.toLowerCase().contains("password"));
        assertTrue(!containsPassword, "UserVO 不得包含 password/passwordHash 字段，实际字段: " + names);
    }

    @Test
    void voShouldExposeExactlyAllowedFields() {
        Set<String> expected = Set.of(
                "id", "username", "email", "nickname",
                "status", "lastLoginAt", "createdAt", "updatedAt");
        Set<String> actual = Arrays.stream(UserVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
    }
}
