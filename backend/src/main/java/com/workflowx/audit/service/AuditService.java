package com.workflowx.audit.service;

import com.workflowx.audit.vo.AuditLogVO;
import com.workflowx.common.web.PageVO;

/**
 * 审计日志领域能力（P10-03; ADR-020）。
 * 事务: record 与主业务共事务（REQUIRED）——业务回滚不留"成功"审计；审计 insert 失败上抛回滚主业务（不吞）。
 * 数据范围: audit_logs 为高敏感系统资源，查询仅限 audit:list/get authority（ADMIN 绑定）。
 */
public interface AuditService {

    /**
     * 独立事务记录（REQUIRES_NEW）：专用于"失败事实本身必须幸存"的场景（如登录失败——
     * 业务事务回滚不应抹掉失败审计）。除该语义外一律用 record（同事务）。
     */
    void recordStandalone(String module, String action, String target, String summary, boolean success, Long userId);

    /**
     * 记录高价值操作事实（业务 Service 接线调用）。
     * Web 上下文（method/uri/ip/userAgent/traceId）由本服务自动采集；无请求上下文时为 null。
     * summary 必须为白名单字段组装的业务摘要——严禁密码/令牌/Authorization 等敏感数据。
     *
     * @param userId  操作人；匿名场景（如登录失败未识别用户）为 null
     */
    void record(String module, String action, String target, String summary, boolean success, Long userId);

    /** 审计查询（audit:list）：多条件筛选 + created_at DESC, id DESC 稳定排序 + 分页。 */
    PageVO<AuditLogVO> query(AuditQueryParams params);

    /** 单条详情（audit:get）。 */
    AuditLogVO getById(Long id);

    /** 查询参数（独立 record 便于测试构造）。 */
    record AuditQueryParams(
            Long operatorId,
            String module,
            String action,
            Boolean success,
            String target,
            String traceId,
            java.time.LocalDateTime beginTime,
            java.time.LocalDateTime endTime,
            long page,
            long size) {
    }
}
