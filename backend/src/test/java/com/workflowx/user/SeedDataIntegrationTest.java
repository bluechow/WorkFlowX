package com.workflowx.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * dev 种子数据集成测试（P2-01）。
 * 前置条件: 本地基础设施可用（compose MySQL :3307），应用上下文启动时自动执行 Flyway 迁移。
 */
@SpringBootTest
class SeedDataIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedUsersShouldExist() {
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT username FROM users WHERE username IN ('admin', 'user1')", String.class);
        assertTrue(usernames.containsAll(List.of("admin", "user1")), "dev 种子用户缺失: " + usernames);
    }

    @Test
    void seedRolesShouldExist() {
        List<String> codes = jdbcTemplate.queryForList("SELECT code FROM roles", String.class);
        assertTrue(codes.containsAll(List.of("ADMIN", "MEMBER")), "角色种子缺失: " + codes);
    }

    @Test
    void adminShouldHaveAdminRole() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_roles ur
                JOIN users u ON u.id = ur.user_id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.username = 'admin' AND r.code = 'ADMIN'
                """, Integer.class);
        assertEquals(1, count, "admin 应恰好拥有一个 ADMIN 角色");
    }

    @Test
    void user1ShouldHaveMemberRole() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_roles ur
                JOIN users u ON u.id = ur.user_id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.username = 'user1' AND r.code = 'MEMBER'
                """, Integer.class);
        assertEquals(1, count, "user1 应恰好拥有一个 MEMBER 角色");
    }

    @Test
    void seedUsersShouldBeActiveWithBcryptHash() {
        List<MapRow> rows = jdbcTemplate.query(
                "SELECT username, status, password_hash FROM users WHERE username IN ('admin', 'user1')",
                (rs, rowNum) -> new MapRow(
                        rs.getString("username"),
                        rs.getString("status"),
                        rs.getString("password_hash")));
        assertEquals(2, rows.size());
        for (MapRow row : rows) {
            assertEquals("ACTIVE", row.status(), row.username() + " 应为 ACTIVE");
            assertTrue(row.passwordHash().matches("^\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$"),
                    row.username() + " 的 password_hash 不是 BCrypt(10) 格式: " + row.passwordHash());
            assertFalse(row.passwordHash().toLowerCase().contains("@123456"),
                    row.username() + " 的 password_hash 疑似明文");
        }
    }

    private record MapRow(String username, String status, String passwordHash) {
    }
}
