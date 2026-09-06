package com.workflowx.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** User 实体映射测试（P2-01）：必须与 V1 真实表结构一致，不允许假设字段。 */
class UserEntityTest {

    @Test
    void entityShouldMapRealTableColumnsExactly() {
        // V1 users 表真实业务字段（见 V1__identity_core.sql），多一个少一个都算映射错误
        Set<String> expected = Set.of(
                "id", "username", "email", "passwordHash", "nickname",
                "status", "lastLoginAt", "createdAt", "updatedAt");
        Set<String> actual = Arrays.stream(User.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    @Test
    void entityShouldAnnotateRealTableName() {
        TableName tableName = User.class.getAnnotation(TableName.class);
        assertNotNull(tableName);
        assertEquals("users", tableName.value());
    }

    @Test
    void idShouldUseAutoIncrementStrategy() throws Exception {
        Field id = User.class.getDeclaredField("id");
        TableId tableId = id.getAnnotation(TableId.class);
        assertNotNull(tableId);
        assertEquals(com.baomidou.mybatisplus.annotation.IdType.AUTO, tableId.type());
    }

    @Test
    void statusEnumShouldMatchDatabaseDefinition() {
        // V1: ENUM('ACTIVE','DISABLED','LOCKED')
        assertEquals(3, UserStatus.values().length);
        assertEquals("ACTIVE", UserStatus.ACTIVE.name());
        assertEquals("DISABLED", UserStatus.DISABLED.name());
        assertEquals("LOCKED", UserStatus.LOCKED.name());
    }

    @Test
    void entityMustNotContainPlainPasswordField() {
        // 领域模型只允许 passwordHash（对应列 password_hash），禁止出现明文密码字段
        boolean hasPlainPasswordField = Arrays.stream(User.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(n -> n.equalsIgnoreCase("password"));
        assertTrue(!hasPlainPasswordField, "User 实体不得包含明文 password 字段");
    }
}
