package com.workflowx.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 演示数据生成器集成测试（连真实 dev 库，P1 集成测试风格）。
 * 覆盖：数据完整性（各表行数与设计一致、编号计数器同步、关联正确）与幂等性（二次执行行数不变）。
 */
@SpringBootTest
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void seed_createsFullDataset_andIsIdempotent() {
        seeder.seed();

        // 幂等：再跑一遍，行数不得增长
        int usersBefore = count("SELECT COUNT(*) FROM users WHERE username LIKE 'demo\\_%'");
        int issuesBefore = count("SELECT COUNT(*) FROM issues i JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')");
        seeder.seed();
        assertThat(count("SELECT COUNT(*) FROM users WHERE username LIKE 'demo\\_%'")).isEqualTo(usersBefore);
        assertThat(count("SELECT COUNT(*) FROM issues i JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isEqualTo(issuesBefore);

        // 用户：demo_admin + demo_pm + 28 成员 = 30，角色绑定齐全
        assertThat(usersBefore).isEqualTo(30);
        assertThat(count("SELECT COUNT(*) FROM user_roles ur JOIN users u ON ur.user_id = u.id WHERE u.username LIKE 'demo\\_%'"))
                .isEqualTo(30);

        // 组织 3 / 部门 5 / 项目 4 / 成员 9+7+8+5=29 + demo_admin 每项目 MANAGER +4 = 33
        assertThat(count("SELECT COUNT(*) FROM organizations WHERE code IN ('STARLIGHT','YUNFAN','BLUEWHALE')")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM departments d JOIN organizations o ON d.org_id = o.id WHERE o.code IN ('STARLIGHT','YUNFAN')")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM project_members pm JOIN projects p ON pm.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isEqualTo(33);

        // Issue：48 条；编号 1..n 连续且 issue_seq 同步
        assertThat(issuesBefore).isEqualTo(48);
        assertThat(count("""
                SELECT COUNT(*) FROM issues i JOIN projects p ON i.project_id = p.id
                WHERE p.`key` = 'CAMPUS' AND i.issue_no <= (SELECT issue_seq FROM projects WHERE id = p.id)
                """)).isEqualTo(16);
        assertThat(count("SELECT issue_seq FROM projects WHERE `key` = 'MBANK'")).isEqualTo(12);

        // 业务一致性：assignee 必须是项目成员（演示数据承诺）
        assertThat(count("""
                SELECT COUNT(*) FROM issues i
                JOIN projects p ON i.project_id = p.id
                LEFT JOIN project_members pm ON pm.project_id = i.project_id AND pm.user_id = i.assignee_id
                WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')
                  AND i.assignee_id IS NOT NULL AND pm.user_id IS NULL
                """)).isZero();

        // 评论 / 通知 / 审计 / 附件
        assertThat(count("SELECT COUNT(*) FROM issue_comments c JOIN issues i ON c.issue_id = i.id JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isGreaterThanOrEqualTo(15);
        assertThat(count("""
                SELECT COUNT(*) FROM notifications n JOIN users u ON n.recipient_id = u.id
                WHERE u.username LIKE 'demo\\_%' AND n.type = 'ISSUE_ASSIGNED'
                """)).isEqualTo(12);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE user_agent = 'demo-seeder/1.0'")).isEqualTo(12);
        assertThat(count("SELECT COUNT(*) FROM attachments WHERE object_key LIKE 'issues/%' AND uploader_id IN (SELECT id FROM users WHERE username = 'demo_pm')"))
                .isEqualTo(4);

        // 用例库：36 用例 + 10 目录 + 计数器同步
        assertThat(count("SELECT COUNT(*) FROM test_cases tc JOIN projects p ON tc.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK')"))
                .isEqualTo(36);
        assertThat(count("SELECT COUNT(*) FROM test_case_directories d JOIN projects p ON d.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK')"))
                .isEqualTo(10);
        assertThat(count("SELECT testcase_seq FROM projects WHERE `key` = 'SHOP'")).isEqualTo(12);

        // V18：活动流（每项目上限 26 条 → 4 项目共 ~80+，倒序含 CREATE/ASSIGN/TRANSITION/COMMENT）
        assertThat(count("SELECT COUNT(*) FROM activities a JOIN projects p ON a.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isGreaterThanOrEqualTo(80);
        assertThat(count("SELECT COUNT(*) FROM activities WHERE action = 'CREATE' AND summary LIKE '创建了 %'"))
                .isEqualTo(48);

        // V17：标签 4/项目×4、issue_labels 绑定、截止日期（含逾期）、关联 2/项目
        assertThat(count("SELECT COUNT(*) FROM labels l JOIN projects p ON l.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isEqualTo(16);
        assertThat(count("SELECT COUNT(*) FROM issue_labels il JOIN issues i ON il.issue_id = i.id JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isGreaterThan(40);
        assertThat(count("SELECT COUNT(*) FROM issues i JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS') AND i.due_date IS NOT NULL"))
                .isGreaterThan(10);
        assertThat(count("SELECT COUNT(*) FROM issues i JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS') AND i.due_date < NOW()"))
                .isGreaterThan(3);
        assertThat(count("SELECT COUNT(*) FROM issue_links il JOIN issues i ON il.source_issue_id = i.id JOIN projects p ON i.project_id = p.id WHERE p.`key` IN ('CAMPUS','SHOP','MBANK','DEVOPS')"))
                .isEqualTo(8);

        // 计划：2 个（RUNNING 10 项 / COMPLETED 4 项），FAIL 项必须关联同项目 Bug
        assertThat(count("SELECT COUNT(*) FROM test_plans WHERE name IN ('MBANK V2.3 版本回归测试','智慧校园 V1.0 冒烟测试')")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM test_plan_items tpi JOIN test_plans tp ON tpi.plan_id = tp.id WHERE tp.name = 'MBANK V2.3 版本回归测试'"))
                .isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM test_plan_items tpi JOIN test_plans tp ON tpi.plan_id = tp.id WHERE tp.name = '智慧校园 V1.0 冒烟测试'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM test_plan_items tpi JOIN test_plans tp ON tpi.plan_id = tp.id WHERE tpi.result = 'FAIL'")).isEqualTo(4);
        assertThat(count("""
                SELECT COUNT(*) FROM test_plan_items tpi
                JOIN test_plans tp ON tpi.plan_id = tp.id
                JOIN issues i ON tpi.issue_id = i.id
                WHERE tpi.result = 'FAIL' AND i.project_id = tp.project_id
                """)).isEqualTo(4);
    }
}
