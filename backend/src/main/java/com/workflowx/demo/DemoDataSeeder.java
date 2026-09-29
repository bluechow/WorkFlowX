package com.workflowx.demo;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workflowx.common.storage.StorageService;

/**
 * 演示数据生成器（V1.2 Phase A-①；ADR-024）。
 *
 * 目标：让每个模块开箱即有真实体量的数据（3 组织 / 30 用户 / 4 项目 / 48 Issue /
 * 评论 / 附件 / 通知 / 审计 / 36 用例 / 2 测试计划），服务于日常演示、教学与后续
 * 测试活动——测试者面对的应当是一个"有数据的项目"，而不是空库。
 *
 * 设计约束：
 * - 幂等：以 demo_admin 用户存在为标记，重复执行直接跳过（不覆盖手工数据）；
 * - 原子：seed 过程包在事务里，中途失败整体回滚（MinIO 对象除外，残留可忽略）；
 * - 直插 SQL（JdbcTemplate）：演示数据是一次性开发期产物，绕过 Service 校验层，
 *   但列名/枚举取值与 V1~V16 迁移严格一致；
 * - 业务一致性手工保证：编号连续（issue_seq/testcase_seq 同步落库）、
 *   assignee 取自项目成员、测试计划 FAIL 关联同项目 Issue；
 * - 固定随机种子（42）：同一份代码在任何环境生成完全一致的数据。
 */
@Service
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    private final Random random = new Random(42);

    private String passwordHash;
    private long roleIdAdmin;
    private long roleIdMember;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, StorageService storageService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
    }

    /** 幂等标记：demo_admin 是否已存在 */
    public boolean isSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'demo_admin'", Integer.class);
        return count != null && count > 0;
    }

    /** synchronized + 事务：并发只生成一份；任一步失败整体回滚，不留半截数据 */
    @Transactional
    public synchronized void seed() {
        if (isSeeded()) {
            log.info("[demo-seed] 演示数据已存在（demo_admin），跳过。如需重建请先清理 demo_* 数据。");
            return;
        }
        log.info("[demo-seed] 开始生成演示数据 ……");
        long start = System.currentTimeMillis();

        this.passwordHash = passwordEncoder.encode("Demo@123456");
        this.roleIdAdmin = requireId("SELECT id FROM roles WHERE code = 'ADMIN'");
        this.roleIdMember = requireId("SELECT id FROM roles WHERE code = 'MEMBER'");

        seedUsers();
        seedOrganizations();
        seedProjects();
        seedIssues();
        seedWorkItemEnhancements();
        seedComments();
        seedNotifications();
        seedAuditLogs();
        seedTestCasesAndPlans();
        seedAttachments();
        seedActivities();

        log.info("[demo-seed] 演示数据生成完成，耗时 {} ms", System.currentTimeMillis() - start);
    }

    // ==================== 用户 ====================

    private static final String[] NICKNAMES = {
            "张伟", "李娜", "王强", "刘洋", "陈静", "杨帆", "赵磊", "黄敏", "周杰", "吴霞",
            "徐鹏", "孙丽", "马超", "朱婷", "胡军", "郭欣", "何平", "高翔", "林悦", "罗斌",
            "郑爽", "梁晨", "宋佳", "唐磊", "韩雪", "冯军", "曹颖", "彭飞"
    };

    /** memberIds[0] = demo_pm，[1..28] = demo_u01..u28（demo_admin 不入池，仅做组织 OWNER） */
    private final List<Long> memberIds = new ArrayList<>();
    private long demoAdminId;
    private long demoPmId;
    private long org1Id;
    private long org2Id;
    private long org3Id;

    private void seedUsers() {
        demoAdminId = insertUser("demo_admin", "演示管理员", true);
        demoPmId = insertUser("demo_pm", "沈慧（项目经理）", false);
        memberIds.add(demoPmId);
        for (int i = 0; i < NICKNAMES.length; i++) {
            memberIds.add(insertUser(String.format("demo_u%02d", i + 1), NICKNAMES[i], false));
        }
    }

    private long insertUser(String username, String nickname, boolean admin) {
        Timestamp created = daysAgo(30 + random.nextInt(60));
        Timestamp lastLogin = random.nextInt(3) == 0 ? daysAgo(random.nextInt(7)) : null;
        long id = insert("""
                INSERT INTO users (username, email, password_hash, nickname, status, last_login_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                username, username + "@demo.workflowx.local", passwordHash, nickname,
                lastLogin, created, created);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                id, admin ? roleIdAdmin : roleIdMember);
        return id;
    }

    // ==================== 组织 / 部门 / 组织成员 ====================

    private void seedOrganizations() {
        org1Id = insert("INSERT INTO organizations (name, code, owner_id, description, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                "星辰科技股份有限公司", "STARLIGHT", demoAdminId, "演示组织：综合软件研发企业", daysAgo(90), daysAgo(90));
        org2Id = insert("INSERT INTO organizations (name, code, owner_id, description, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                "云帆信息技术有限公司", "YUNFAN", demoPmId, "演示组织：电商技术公司", daysAgo(80), daysAgo(80));
        org3Id = insert("INSERT INTO organizations (name, code, owner_id, description, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                "蓝鲸软件工作室", "BLUEWHALE", memberIds.get(2), "演示组织：小型外包团队", daysAgo(70), daysAgo(70));

        long rd = insert("INSERT INTO departments (org_id, parent_id, name, code, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?)",
                org1Id, "研发部", "RD", daysAgo(89), daysAgo(89));
        long qa = insert("INSERT INTO departments (org_id, parent_id, name, code, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?)",
                org1Id, "测试部", "QA", daysAgo(89), daysAgo(89));
        long pd = insert("INSERT INTO departments (org_id, parent_id, name, code, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?)",
                org1Id, "产品部", "PD", daysAgo(89), daysAgo(89));
        insert("INSERT INTO departments (org_id, parent_id, name, code, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?)",
                org1Id, "设计部", "DES", daysAgo(89), daysAgo(89));
        long rd2 = insert("INSERT INTO departments (org_id, parent_id, name, code, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?)",
                org2Id, "研发部", "RD2", daysAgo(79), daysAgo(79));

        // org1：30 人全员（demo_admin 为 OWNER，3 名 ADMIN，其余按部门分配）
        addOrgMember(org1Id, demoAdminId, "OWNER", null, 60);
        long[] org1Admins = { memberIds.get(1), memberIds.get(6), memberIds.get(11) };
        for (long uid : memberIds) {
            String role = contains(org1Admins, uid) ? "ADMIN" : "MEMBER";
            Long dept = switch ((int) (uid % 4)) { case 0 -> rd; case 1 -> qa; case 2 -> pd; default -> null; };
            if (uid == demoPmId) dept = pd;
            addOrgMember(org1Id, uid, role, dept, 60);
        }
        // org2：demo_pm OWNER + 5 名成员（研发部）
        addOrgMember(org2Id, demoPmId, "OWNER", null, 55);
        for (int i = 13; i <= 17; i++) addOrgMember(org2Id, memberIds.get(i), "MEMBER", rd2, 55);
        // org3：u02 为 OWNER + 3 名成员
        addOrgMember(org3Id, memberIds.get(2), "OWNER", null, 50);
        for (int i = 20; i <= 22; i++) addOrgMember(org3Id, memberIds.get(i), "MEMBER", null, 50);
    }

    private void addOrgMember(long orgId, long userId, String role, Long deptId, int maxDaysAgo) {
        jdbc.update("INSERT INTO organization_members (org_id, user_id, role, department_id, created_at) VALUES (?,?,?,?,?)",
                orgId, userId, role, deptId, daysAgo(maxDaysAgo + random.nextInt(10)));
    }

    // ==================== 项目 / 项目成员 ====================

    /** key, name, org 序号(1|2|3), owner 引用, 描述 */
    private static final String[][] PROJECTS = {
            {"CAMPUS", "智慧校园管理系统", "1", "demo_pm", "面向高校的教务/选课/成绩一体化管理平台（演示项目）"},
            {"SHOP",   "云帆电商平台",     "1", "u03",     "B2C 电商平台：商品/订单/营销/支付（演示项目）"},
            {"MBANK",  "移动银行 App",     "1", "u07",     "手机银行客户端 V2.3 迭代（演示项目）"},
            {"DEVOPS", "DevOps 内部平台",  "2", "demo_pm", "CI/CD 流水线与制品库一体化平台（演示项目）"},
    };

    private final List<Long> projectIds = new ArrayList<>();
    private final List<String> projectKeys = new ArrayList<>();
    private final List<List<Long>> projectMemberIds = new ArrayList<>();

    private void seedProjects() {
        for (int i = 0; i < PROJECTS.length; i++) projectMemberIds.add(new ArrayList<>());
        for (int p = 0; p < PROJECTS.length; p++) {
            String[] def = PROJECTS[p];
            long orgId = switch (def[2]) { case "1" -> org1Id; case "2" -> org2Id; default -> org3Id; };
            long owner = switch (def[3]) {
                    case "demo_admin" -> demoAdminId;
                    case "demo_pm" -> demoPmId;
                    case "u03" -> memberIds.get(3);
                    default -> memberIds.get(7);   // u07
            };
            long pid = insert("""
                    INSERT INTO projects (org_id, `key`, name, description, status, owner_id, issue_seq, testcase_seq, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, 0, ?, ?)
                    """,
                    orgId, def[0], def[1], def[4], owner, daysAgo(55 - p * 5), daysAgo(55 - p * 5));
            projectIds.add(pid);
            projectKeys.add(def[0]);
            addProjectMember(p, pid, owner, "OWNER");
            // 演示管理员以 MANAGER 身份加入全部项目：既有全部权限又是成员，
            // 避免"有 authority 无成员资格"导致演示时写操作被数据级 403 拦截
            addProjectMember(p, pid, demoAdminId, "MANAGER");
            int[] extra = switch (def[0]) {
                    case "CAMPUS" -> new int[]{1, 2, 3, 4, 5, 6, 8, 9};
                    case "SHOP"   -> new int[]{1, 4, 5, 9, 10, 11};
                    case "MBANK"  -> new int[]{2, 8, 9, 10, 11, 12, 14};
                    default       -> new int[]{13, 14, 15, 16};   // DEVOPS（org2 成员）
            };
            for (int j = 0; j < extra.length; j++) {
                addProjectMember(p, pid, memberIds.get(extra[j]), j == 0 ? "MANAGER" : "MEMBER");
            }
        }
    }

    private void addProjectMember(int p, long pid, long userId, String role) {
        jdbc.update("INSERT INTO project_members (project_id, user_id, role, created_at) VALUES (?,?,?,?)",
                pid, userId, role, daysAgo(40 + random.nextInt(10)));
        projectMemberIds.get(p).add(userId);
    }

    // ==================== Issue ====================

    /**
     * 每行：项目序号|类型|严重度(仅 BUG)|状态|优先级|标题。数组顺序即 issue_no（1 起）。
     * 约定：MBANK 第 1/2/3 号与 CAMPUS 第 7 号为测试计划 FAIL 关联预留 Bug。
     */
    private static final String[][] ISSUES = {
        {"0","BUG","S2","OPEN","HIGH","学生选课并发提交时接口返回 500"},
        {"0","BUG","S3","IN_PROGRESS","MEDIUM","课程查询分页在第二页出现重复数据"},
        {"0","TASK",null,"OPEN","MEDIUM","编写选课模块接口文档"},
        {"0","FEATURE",null,"IN_PROGRESS","HIGH","支持按学期筛选课表"},
        {"0","BUG","S1","RESOLVED","URGENT","成绩发布后学生端可见他人成绩"},
        {"0","IMPROVEMENT",null,"OPEN","LOW","优化成绩单导出速度"},
        {"0","BUG","S3","TESTING","MEDIUM","教师端考勤打卡时间与服务器时差 8 小时"},
        {"0","TASK",null,"CLOSED","MEDIUM","搭建预发布环境"},
        {"0","FEATURE",null,"OPEN","MEDIUM","新增校园卡余额不足提醒"},
        {"0","BUG","S4","CLOSED","LOW","登录页页脚版权年份错误"},
        {"0","BUG","S2","REOPENED","HIGH","修改密码后旧会话仍然有效"},
        {"0","TASK",null,"IN_PROGRESS","HIGH","数据库慢查询治理：course_schedule 全表扫描"},
        {"0","FEATURE",null,"OPEN","LOW","支持微信小程序扫码登录"},
        {"0","IMPROVEMENT",null,"RESOLVED","MEDIUM","课表页面首屏加载时间从 4s 优化到 1.2s"},
        {"0","BUG","S3","REOPENED","MEDIUM","转专业学生成绩统计归类错误"},
        {"0","TASK",null,"CLOSED","LOW","清理废弃的定时任务"},

        {"1","BUG","S1","IN_PROGRESS","URGENT","秒杀活动超卖：库存扣减存在竞态"},
        {"1","FEATURE",null,"OPEN","HIGH","购物车支持跨店合并结算"},
        {"1","BUG","S2","OPEN","HIGH","优惠券叠加折扣计算金额与展示不一致"},
        {"1","TASK",null,"IN_PROGRESS","MEDIUM","双 11 大促压测方案编写"},
        {"1","BUG","S3","RESOLVED","MEDIUM","订单列表按金额排序失效"},
        {"1","IMPROVEMENT",null,"OPEN","LOW","商品详情页图片懒加载"},
        {"1","FEATURE",null,"OPEN","MEDIUM","接入第三方物流轨迹查询"},
        {"1","BUG","S4","CLOSED","LOW","商品分类页面包屑导航层级显示错误"},
        {"1","TASK",null,"CLOSED","HIGH","支付通道切换演练"},
        {"1","BUG","S2","TESTING","MEDIUM","退款申请提交后状态长时间停留在处理中"},
        {"1","FEATURE",null,"OPEN","LOW","会员积分商城一期"},
        {"1","IMPROVEMENT",null,"OPEN","MEDIUM","搜索联想词接口 P95 优化"},
        {"1","BUG","S3","OPEN","LOW","iOS 端首页轮播图圆角渲染异常"},

        {"2","BUG","S1","OPEN","URGENT","转账金额超过单日限额时未拦截，交易直接受理"},
        {"2","BUG","S2","OPEN","HIGH","指纹登录在部分安卓机型上失效"},
        {"2","BUG","S2","IN_PROGRESS","HIGH","账单明细导出 CSV 中文乱码"},
        {"2","FEATURE",null,"OPEN","HIGH","支持他行卡快捷绑定"},
        {"2","TASK",null,"OPEN","MEDIUM","V2.3 版本回归测试执行"},
        {"2","BUG","S3","OPEN","MEDIUM","理财页面收益率四舍五入显示不精确"},
        {"2","IMPROVEMENT",null,"IN_PROGRESS","MEDIUM","登录页人脸识别前置提示优化"},
        {"2","FEATURE",null,"OPEN","LOW","记账本月度账单分享海报"},
        {"2","BUG","S4","CLOSED","LOW","关于页面版本号显示旧版本"},
        {"2","TASK",null,"IN_PROGRESS","HIGH","等保三级安全测评整改"},
        {"2","BUG","S3","CLOSED","MEDIUM","深色模式下部分按钮对比度过低"},
        {"2","FEATURE",null,"OPEN","MEDIUM","大额转账二次核验流程"},

        {"3","TASK",null,"IN_PROGRESS","HIGH","CI 流水线缓存优化"},
        {"3","BUG","S2","OPEN","MEDIUM","流水线并发触发时制品库索引损坏"},
        {"3","FEATURE",null,"OPEN","MEDIUM","支持钉钉机器人构建通知"},
        {"3","BUG","S3","RESOLVED","LOW","日志检索分页条数显示错误"},
        {"3","IMPROVEMENT",null,"OPEN","LOW","仪表盘加载性能优化"},
        {"3","TASK",null,"CLOSED","MEDIUM","K8s 集群升级至 1.30"},
        {"3","FEATURE",null,"OPEN","HIGH","制品库支持 Helm Chart"},
    };

    private static int projectIssueOffset(int p) {
        return switch (p) { case 0 -> 0; case 1 -> 16; case 2 -> 29; default -> 41; };
    }

    private final List<List<Long>> issueIdsByProject = new ArrayList<>();

    private void seedIssues() {
        for (int i = 0; i < projectIds.size(); i++) issueIdsByProject.add(new ArrayList<>());
        long[] seq = new long[projectIds.size()];
        int issueIndex = 0;
        for (String[] row : ISSUES) {
            int p = Integer.parseInt(row[0]);
            long pid = projectIds.get(p);
            List<Long> members = projectMemberIds.get(p);
            long reporter = members.get(random.nextInt(members.size()));
            boolean open = "OPEN".equals(row[3]);
            Long assignee = (open && issueIndex % 4 == 3) ? null
                    : members.get(random.nextInt(members.size()));
            Timestamp created = daysAgo(1 + random.nextInt(55));
            long no = ++seq[p];
            long id = insert("""
                    INSERT INTO issues (project_id, issue_no, title, description, type, priority, severity,
                                        status, reporter_id, assignee_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    pid, no, row[5],
                    row[5] + "。\n\n复现环境：演示环境（demo 数据）。\n该 Issue 由演示数据生成器创建，用于展示与测试。",
                    row[1], row[4], row[2], row[3], reporter, assignee,
                    created, new Timestamp(created.getTime() + random.nextInt(72) * 3600_000L));
            issueIdsByProject.get(p).add(id);
            issueIndex++;
        }
        for (int p = 0; p < projectIds.size(); p++) {
            jdbc.update("UPDATE projects SET issue_seq = ? WHERE id = ?", seq[p], projectIds.get(p));
        }
    }

    // ==================== V17 工作项增强：标签 / 截止日期 / 关联 ====================

    /** 每项目一组语义化标签（名称, 颜色） */
    private static final String[][] LABEL_DEFS = {
            {"核心链路", "#F56C6C"}, {"体验优化", "#E6A23C"}, {"技术债", "#909399"}, {"安全", "#8B5CF6"},
    };

    private void seedWorkItemEnhancements() {
        // 标签
        long[][] labelIds = new long[projectIds.size()][LABEL_DEFS.length];
        for (int p = 0; p < projectIds.size(); p++) {
            for (int l = 0; l < LABEL_DEFS.length; l++) {
                labelIds[p][l] = insert("INSERT INTO labels (project_id, name, color, created_by, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                        projectIds.get(p), LABEL_DEFS[l][0], LABEL_DEFS[l][1], demoPmId, daysAgo(28), daysAgo(28));
            }
        }
        // 打标 + 截止日期：每项目的 issue 按下标规则绑定；未完结项给截止日期（近 60 天分布，部分逾期）
        for (int p = 0; p < projectIds.size(); p++) {
            List<Long> issues = issueIdsByProject.get(p);
            for (int i = 0; i < issues.size(); i++) {
                long issueId = issues.get(i);
                // 标签：每条至少 1 个，按类型加语义标签
                int labelIdx = i % LABEL_DEFS.length;
                insertIssueLabel(issueId, labelIds[p][labelIdx]);
                if (i % 3 == 0) insertIssueLabel(issueId, labelIds[p][(labelIdx + 1) % LABEL_DEFS.length]);
                // 截止：未完结（非 CLOSED/RESOLVED）的 2/3 给截止日期；前 1/4 逾期制造看板红色警示
                String status = ISSUES[projectIssueOffset(p) + i][3];
                boolean open = !"CLOSED".equals(status) && !"RESOLVED".equals(status);
                if (open && i % 3 != 2) {
                    int offsetDays = (i % 4 == 0) ? -(2 + i % 5) : (3 + i * 2);
                    jdbc.update("UPDATE issues SET due_date = ? WHERE id = ?",
                            Timestamp.valueOf(LocalDateTime.now().plusDays(offsetDays).withHour(18).withMinute(0)), issueId);
                }
            }
        }
        // 关联：每项目第 1 条 BLOCKS 第 2 条（典型"阻塞"故事）；第 3 条 RELATES 第 4 条
        for (int p = 0; p < projectIds.size(); p++) {
            List<Long> issues = issueIdsByProject.get(p);
            if (issues.size() >= 4) {
                jdbc.update("INSERT INTO issue_links (source_issue_id, target_issue_id, link_type, created_by, created_at) VALUES (?,?,?,?,?)",
                        issues.get(0), issues.get(1), "BLOCKS", demoPmId, daysAgo(12));
                jdbc.update("INSERT INTO issue_links (source_issue_id, target_issue_id, link_type, created_by, created_at) VALUES (?,?,?,?,?)",
                        issues.get(2), issues.get(3), "RELATES", demoPmId, daysAgo(10));
            }
        }
    }

    private void insertIssueLabel(long issueId, long labelId) {
        jdbc.update("INSERT INTO issue_labels (issue_id, label_id) VALUES (?,?)", issueId, labelId);
    }

    // ==================== 评论 / 通知 / 审计 ====================

    private static final String[] COMMENT_POOL = {
            "本地环境无法复现，麻烦补充浏览器版本与操作步骤。",
            "已定位：入口参数未做非空校验，修复中。",
            "这个问题在测试环境也可以复现，优先级建议提高。",
            "关联需求文档 v2.1 第 4.3 节，请产品确认预期行为。",
            "修复已合入 dev 分支，请在下轮回归中关注。",
            "补充日志：ERROR 发生在 14:32:07，traceId 已上传附件。",
            "客户催得紧，本周内需要给出临时规避方案。",
            "已验证通过，可关闭。",
    };

    private void seedComments() {
        int count = 0;
        for (int p = 0; p < issueIdsByProject.size(); p++) {
            List<Long> issues = issueIdsByProject.get(p);
            List<Long> members = projectMemberIds.get(p);
            for (int i = 0; i < issues.size() && count < 20; i++) {
                int comments = (i % 3 == 0) ? 3 : (i % 3 == 1 ? 1 : 0);
                for (int c = 0; c < comments && count < 20; c++) {
                    jdbc.update("INSERT INTO issue_comments (issue_id, author_id, content, created_at, updated_at) VALUES (?,?,?,?,?)",
                            issues.get(i), members.get(random.nextInt(members.size())),
                            COMMENT_POOL[(i + c) % COMMENT_POOL.length],
                            daysAgo(random.nextInt(20)), daysAgo(random.nextInt(10)));
                    count++;
                }
            }
        }
    }

    private void seedNotifications() {
        for (int p = 0; p < issueIdsByProject.size(); p++) {
            String key = projectKeys.get(p);
            List<Long> issues = issueIdsByProject.get(p);
            for (int i = 0; i < Math.min(3, issues.size()); i++) {
                boolean read = i == 0;
                jdbc.update("""
                        INSERT INTO notifications (recipient_id, type, title, content, related_type, related_id, is_read, read_at, created_at)
                        VALUES (?, 'ISSUE_ASSIGNED', 'Issue 已分派给您', ?, 'ISSUE', ?, ?, ?, ?)
                        """,
                        memberIds.get(random.nextInt(memberIds.size())),
                        String.format("%s-%d %s（由 #%d 分派）", key, i + 1, ISSUES[projectIssueOffset(p) + i][5], demoPmId),
                        issues.get(i), read ? 1 : 0, read ? daysAgo(1) : null, daysAgo(2 + i));
            }
        }
    }

    private void seedAuditLogs() {
        for (int i = 0; i < 12; i++) {
            long uid = memberIds.get(random.nextInt(memberIds.size()));
            boolean login = i % 3 != 2;
            long pid = projectIds.get(i % projectIds.size());
            jdbc.update("""
                    INSERT INTO audit_logs (user_id, module, action, http_method, uri, ip, target, summary, success, trace_id, user_agent, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,NULL,?,?)
                    """,
                    uid, login ? "AUTH" : "ISSUE", login ? "LOGIN" : "CREATE", "POST",
                    login ? "/api/v1/auth/login" : "/api/v1/projects/" + pid + "/issues",
                    "127.0.0.1", login ? "user:" + uid : null,
                    login ? "演示登录" : "演示数据操作", 1,
                    "demo-seeder/1.0", daysAgo(random.nextInt(14)));
        }
    }

    // ==================== 用例库 / 测试计划 ====================

    /** 项目序号|目录名（均为根目录） */
    private static final Object[][] TC_DIRS = {
        {0, "登录认证"}, {0, "课程管理"}, {0, "成绩管理"}, {0, "冒烟用例"},
        {1, "用户中心"}, {1, "商品订单"}, {1, "冒烟用例"},
        {2, "转账汇款"}, {2, "账户查询"}, {2, "登录安全"},
    };

    /** 项目|目录序号(1 起)|标题|类型|优先级|状态 */
    private static final String[][] TEST_CASES = {
        {"0","1","正常账号密码登录成功","SMOKE","HIGH","ACTIVE"},
        {"0","1","密码错误 5 次账号锁定","FUNCTIONAL","HIGH","ACTIVE"},
        {"0","1","禁用账号登录被拒绝","SECURITY","MEDIUM","ACTIVE"},
        {"0","2","创建新课程并发布","FUNCTIONAL","MEDIUM","ACTIVE"},
        {"0","2","学生选课容量满后提示已满","FUNCTIONAL","HIGH","ACTIVE"},
        {"0","2","课程检索支持关键字模糊匹配","FUNCTIONAL","LOW","ACTIVE"},
        {"0","2","删除课程后学生端不再显示","REGRESSION","MEDIUM","ACTIVE"},
        {"0","3","教师录入成绩后学生端可查询","SMOKE","HIGH","ACTIVE"},
        {"0","3","成绩单导出 Excel 数据完整性","FUNCTIONAL","MEDIUM","ACTIVE"},
        {"0","3","GPA 计算精度校验","FUNCTIONAL","LOW","DRAFT"},
        {"0","4","核心页面可达性冒烟","SMOKE","CRITICAL","ACTIVE"},
        {"0","4","数据库连接健康检查","SMOKE","HIGH","ACTIVE"},
        {"0","4","登录-选课-查成绩主链路冒烟","SMOKE","CRITICAL","ACTIVE"},
        {"0","4","系统时间一致性检查","SMOKE","LOW","ACTIVE"},
        {"1","5","注册并激活账号","SMOKE","HIGH","ACTIVE"},
        {"1","5","头像上传格式校验","FUNCTIONAL","LOW","ACTIVE"},
        {"1","5","收货地址增删改查","FUNCTIONAL","MEDIUM","ACTIVE"},
        {"1","5","修改登录密码后旧密码失效","SECURITY","HIGH","ACTIVE"},
        {"1","6","商品搜索-加购-下单主链路","SMOKE","CRITICAL","ACTIVE"},
        {"1","6","库存不足下单拦截","FUNCTIONAL","HIGH","ACTIVE"},
        {"1","6","优惠券抵扣金额计算","FUNCTIONAL","HIGH","ACTIVE"},
        {"1","6","订单取消后库存回滚","REGRESSION","HIGH","ACTIVE"},
        {"1","6","物流信息同步展示","FUNCTIONAL","LOW","ACTIVE"},
        {"1","7","首页核心模块加载","SMOKE","CRITICAL","ACTIVE"},
        {"1","7","支付沙箱联通性","SMOKE","HIGH","ACTIVE"},
        {"1","7","核心接口健康检查","SMOKE","HIGH","ACTIVE"},
        {"2","8","同行转账成功","SMOKE","CRITICAL","ACTIVE"},
        {"2","8","超单日限额转账拦截","FUNCTIONAL","CRITICAL","ACTIVE"},
        {"2","8","收款账号不存在时报错提示","FUNCTIONAL","MEDIUM","ACTIVE"},
        {"2","8","转账撤销窗口期内退回","REGRESSION","HIGH","ACTIVE"},
        {"2","9","余额查询与柜面一致","SMOKE","CRITICAL","ACTIVE"},
        {"2","9","账单明细分页与筛选","FUNCTIONAL","MEDIUM","ACTIVE"},
        {"2","9","账单导出 CSV 内容校验","FUNCTIONAL","HIGH","ACTIVE"},
        {"2","10","指纹登录成功进入首页","SMOKE","HIGH","ACTIVE"},
        {"2","10","连续错误手势密码锁定","SECURITY","HIGH","ACTIVE"},
        {"2","10","会话超时自动退出","SECURITY","MEDIUM","ACTIVE"},
    };

    private final List<Long> campusCaseIds = new ArrayList<>();
    private final List<Long> mbankCaseIds = new ArrayList<>();

    private void seedTestCasesAndPlans() {
        long creator = memberIds.get(1);
        long[] dirIds = new long[TC_DIRS.length];
        for (int d = 0; d < TC_DIRS.length; d++) {
            dirIds[d] = insert("INSERT INTO test_case_directories (project_id, parent_id, name, created_by, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?)",
                    projectIds.get((Integer) TC_DIRS[d][0]), TC_DIRS[d][1], creator, daysAgo(30), daysAgo(30));
        }
        long[] seq = new long[projectIds.size()];
        for (String[] tc : TEST_CASES) {
            int p = Integer.parseInt(tc[0]);
            long no = ++seq[p];
            long id = insert("""
                    INSERT INTO test_cases (project_id, directory_id, testcase_no, title, preconditions, steps, expected,
                                            case_type, priority, status, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    projectIds.get(p), dirIds[Integer.parseInt(tc[1]) - 1], no, tc[2],
                    "演示数据：被测系统处于可用状态",
                    "1. 按用例前置准备数据\n2. 执行操作\n3. 校验结果",
                    "行为与预期一致，无异常日志",
                    tc[3], tc[4], tc[5], creator, daysAgo(25), daysAgo(25));
            if (p == 0) campusCaseIds.add(id);
            if (p == 2) mbankCaseIds.add(id);
        }
        for (int p = 0; p < projectIds.size(); p++) {
            jdbc.update("UPDATE projects SET testcase_seq = ? WHERE id = ?", seq[p], projectIds.get(p));
        }
        seedTestPlans();
    }

    private void seedTestPlans() {
        // 计划 1：MBANK V2.3 回归（RUNNING：5 PASS / 3 FAIL / 1 BLOCKED / 1 PENDING）
        long plan1 = insert("INSERT INTO test_plans (project_id, name, status, created_by, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                projectIds.get(2), "MBANK V2.3 版本回归测试", "RUNNING", memberIds.get(7), daysAgo(6), daysAgo(1));
        // {结果, 关联 Bug 的 issue_no(仅 FAIL), 备注}；MBANK-1 超限 / MBANK-3 CSV 乱码 / MBANK-2 指纹登录
        Object[][] results1 = {
                {"PASS", null, "同行转账 5 笔全部成功"},
                {"FAIL", 1L, "超限未拦截，已提 Bug（MBANK-1）"},
                {"PASS", null, null},
                {"BLOCKED", null, "撤销接口联调环境不可用"},
                {"PASS", null, null},
                {"PASS", null, null},
                {"FAIL", 3L, "导出中文乱码，已提 Bug（MBANK-3）"},
                {"FAIL", 2L, "三款安卓机型指纹登录失败，已提 Bug（MBANK-2）"},
                {"PASS", null, null},
                {null, null, null},
        };
        List<Long> mbankIssues = issueIdsByProject.get(2);
        for (int i = 0; i < results1.length; i++) {
            Object[] r = results1[i];
            String result = (String) r[0];
            Long issueNo = (Long) r[1];
            jdbc.update("""
                    INSERT INTO test_plan_items (plan_id, case_id, result, executed_by, executed_at, note, issue_id, created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    plan1, mbankCaseIds.get(i), result == null ? "PENDING" : result,
                    result == null ? null : memberIds.get(8 + i % 3),
                    result == null ? null : daysAgo(1 + i / 3),
                    r[2], issueNo == null ? null : mbankIssues.get((int) (issueNo - 1)),
                    daysAgo(6));
        }

        // 计划 2：CAMPUS V1.0 冒烟（COMPLETED：3 PASS / 1 FAIL）
        long plan2 = insert("INSERT INTO test_plans (project_id, name, status, created_by, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                projectIds.get(0), "智慧校园 V1.0 冒烟测试", "COMPLETED", demoPmId, daysAgo(10), daysAgo(4));
        Object[][] results2 = {
                {"PASS", null, null},
                {"PASS", null, null},
                {"PASS", null, null},
                {"FAIL", 7L, "应用服务器与 DB 时间不一致，考勤偏差 8 小时（CAMPUS-7）"},
        };
        List<Long> campusIssues = issueIdsByProject.get(0);
        for (int i = 0; i < results2.length; i++) {
            Object[] r = results2[i];
            Long issueNo = (Long) r[1];
            jdbc.update("""
                    INSERT INTO test_plan_items (plan_id, case_id, result, executed_by, executed_at, note, issue_id, created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    plan2, campusCaseIds.get(10 + i), r[0], i % 2 == 0 ? demoPmId : memberIds.get(5),
                    daysAgo(6 - i), r[2],
                    issueNo == null ? null : campusIssues.get((int) (issueNo - 1)),
                    daysAgo(10));
        }
    }

    // ==================== 活动流（V18 演示 fixture）====================

    /** 为每个项目生成近 30 天的活动流：CREATE 全量 + 附加事件限量（时间倒推分布） */
    private void seedActivities() {
        for (int p = 0; p < projectIds.size(); p++) {
            List<Long> issues = issueIdsByProject.get(p);
            int extras = 0;
            for (int i = 0; i < issues.size(); i++) {
                long issueId = issues.get(i);
                String key = projectKeys.get(p);
                long no = i + 1;
                String title = ISSUES[projectIssueOffset(p) + i][5];
                String[] row = ISSUES[projectIssueOffset(p) + i];
                // 创建：每条工作项必有
                insertActivity(p, issueId, no, "CREATE", "创建了 " + key + "-" + no + " " + title, 26 - i);
                // 以下附加事件限量 12/项目，避免活动页被单项目刷屏
                boolean open = "OPEN".equals(row[3]);
                if (!(open && i % 4 == 3) && extras < 12) {
                    insertActivity(p, issueId, no, "ASSIGN", "将 " + key + "-" + no + " 分派给 #" + (80 + i % 6), 25 - i);
                    extras++;
                }
                if (!"OPEN".equals(row[3]) && extras < 12) {
                    insertActivity(p, issueId, no, "TRANSITION", "将 #" + no + " 流转为 " + row[3], 24 - i);
                    extras++;
                }
                if (i % 3 == 0 && extras < 12) {
                    insertActivity(p, issueId, no, "COMMENT", "添加了评论", 23 - i);
                    extras++;
                }
            }
        }
    }

    private void insertActivity(int p, long issueId, long no, String action, String summary, int daysAgo) {
        jdbc.update("INSERT INTO activities (project_id, issue_id, actor_id, action, target, summary, created_at) VALUES (?,?,?,?,?,?,?)",
                projectIds.get(p), issueId, memberIds.get(Math.abs(summary.hashCode() + (int) no) % memberIds.size()),
                action, "issue:" + issueId, summary, daysAgo(daysAgo));
    }

    // ==================== 附件（MinIO 直传 + 元数据） ====================

    private void seedAttachments() {
        long campusBug = issueIdsByProject.get(0).get(4);   // CAMPUS-5 成绩越权
        long shopBug = issueIdsByProject.get(1).get(0);     // SHOP-1 超卖
        long mbankBug = issueIdsByProject.get(2).get(2);    // MBANK-3 CSV 乱码
        uploadAttachment(campusBug, "复现步骤.txt", "text/plain",
                "1. 使用学生 A 登录\n2. 直接访问学生 B 的成绩详情 URL\n3. 观察响应 200 且返回他人数据\n");
        uploadAttachment(campusBug, "接口响应.json", "application/json",
                "{\"code\":200,\"message\":\"success\",\"data\":{\"studentId\":2,\"name\":\"他人\"}}\n");
        uploadAttachment(shopBug, "压测记录.txt", "text/plain",
                "并发 500 下超卖 37 件；JMeter 聚合报告摘要见内部分享。\n");
        uploadAttachment(mbankBug, "乱码说明.txt", "text/plain",
                "Windows Excel 直接打开 CSV 出现中文乱码，UTF-8 BOM 缺失导致。\n");
    }

    private void uploadAttachment(long issueId, String fileName, String contentType, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String objectKey = "issues/" + issueId + "/" + java.util.UUID.randomUUID() + "-" + fileName;
        storageService.upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
        jdbc.update("INSERT INTO attachments (issue_id, object_key, file_name, file_size, content_type, uploader_id, created_at) VALUES (?,?,?,?,?,?,?)",
                issueId, objectKey, fileName, bytes.length, contentType, demoPmId, daysAgo(random.nextInt(10)));
    }

    // ==================== 工具 ====================

    private long insert(String sql, Object... params) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey(), "insert 未返回主键: " + sql).longValue();
    }

    private long requireId(String sql) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class), sql);
    }

    private Timestamp daysAgo(int days) {
        return Timestamp.valueOf(LocalDateTime.now().minusDays(days).minusHours(random.nextInt(10)));
    }

    private boolean contains(long[] arr, long v) {
        for (long x : arr) if (x == v) return true;
        return false;
    }
}
