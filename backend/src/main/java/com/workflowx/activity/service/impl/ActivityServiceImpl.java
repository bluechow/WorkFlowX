package com.workflowx.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.activity.entity.Activity;
import com.workflowx.activity.mapper.ActivityMapper;
import com.workflowx.activity.service.ActivityService;
import com.workflowx.activity.vo.ActivityVO;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 活动流实现（V18）。
 * record 在业务事务内：与主业务同生共死（回滚不留孤儿活动）；写失败仅记日志不抛
 * （活动是辅助信息，不阻断主流程）。issueNo 批量回填避免 N+1。
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityServiceImpl.class);

    private final ActivityMapper activityMapper;
    private final IssueMapper issueMapper;

    @Override
    public void record(Long projectId, Long issueId, Long actorId, Activity.Action action,
                       String target, String summary) {
        try {
            Activity activity = new Activity();
            activity.setProjectId(projectId);
            activity.setIssueId(issueId);
            activity.setActorId(actorId);
            activity.setAction(action);
            activity.setTarget(target);
            activity.setSummary(summary);
            activityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("[activity] 记录失败（不阻断主业务）: {}", e.getMessage());
        }
    }

    @Override
    public List<ActivityVO> page(Long projectId, int limit) {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getProjectId, projectId)
                        .orderByDesc(Activity::getId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> issueIds = activities.stream()
                .map(Activity::getIssueId).filter(id -> id != null).distinct().toList();
        Map<Long, Long> issueNoById = issueIds.isEmpty() ? Map.of()
                : issueMapper.selectBatchIds(issueIds).stream()
                        .collect(Collectors.toMap(Issue::getId, Issue::getIssueNo));
        return activities.stream()
                .map(a -> ActivityVO.of(a, a.getIssueId() == null ? null : issueNoById.get(a.getIssueId())))
                .toList();
    }
}
