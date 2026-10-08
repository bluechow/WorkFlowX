package com.workflowx.quality.service;

import com.workflowx.quality.dto.QualityDTOs.HeatmapVO;
import com.workflowx.quality.dto.QualityDTOs.TraceabilityVO;

/**
 * 质量分析服务（创新点）：覆盖率热力图 + 需求-测试追溯矩阵。
 * 数据全部来自现有用例库/计划执行/Bug 关联的真实聚合，无任何伪造。
 */
public interface QualityService {

    /** 热力图：按用例目录聚合覆盖统计 + 风险评级 */
    HeatmapVO heatmap(Long projectId);

    /** 追溯矩阵：目录 × 用例 × 最新执行结果 × 关联 Bug */
    TraceabilityVO traceability(Long projectId);
}
