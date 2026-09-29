package com.workflowx.activity.service;

import com.workflowx.activity.entity.Activity;
import com.workflowx.activity.vo.ActivityVO;

import java.util.List;

/**
 * 活动流服务（V18，Final Edition FP-2）。
 * record：业务事务内调用（同生共死）；page：读侧（issue:list 权限，Controller 层）。
 */
public interface ActivityService {

    /** 记录活动（事务内；失败不阻断主业务——内部捕获） */
    void record(Long projectId, Long issueId, Long actorId, Activity.Action action,
                String target, String summary);

    /** 项目活动分页（倒序；issueNo 由关联 issues 表回填，删除的工作项显示 —） */
    List<ActivityVO> page(Long projectId, int limit);
}
