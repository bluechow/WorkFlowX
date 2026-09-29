package com.workflowx.issue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.issue.dto.LabelRequests;
import com.workflowx.issue.entity.Label;
import com.workflowx.issue.mapper.LabelMapper;
import com.workflowx.issue.service.LabelService;
import com.workflowx.issue.vo.LabelVO;
import com.workflowx.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签服务实现（V17）。项目内名称唯一：先查后写 + uk_labels_project_name 并发兜底（409）。
 */
@Service
@RequiredArgsConstructor
public class LabelServiceImpl implements LabelService {

    private static final String DEFAULT_COLOR = "#909399";

    private final LabelMapper labelMapper;
    private final ProjectMapper projectMapper;

    @Override
    public List<LabelVO> list(Long projectId) {
        requireProject(projectId);
        return labelMapper.selectList(new LambdaQueryWrapper<Label>()
                        .eq(Label::getProjectId, projectId)
                        .orderByAsc(Label::getName))
                .stream().map(LabelVO::from).toList();
    }

    @Override
    public LabelVO create(Long projectId, LabelRequests.CreateLabelRequest request, Long operatorId) {
        requireProject(projectId);
        Label label = new Label();
        label.setProjectId(projectId);
        label.setName(request.name().trim());
        label.setColor(request.color() == null || request.color().isBlank() ? DEFAULT_COLOR : request.color());
        label.setCreatedBy(operatorId);
        try {
            labelMapper.insert(label);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "标签名已存在");
        }
        return LabelVO.from(label);
    }

    @Override
    public LabelVO update(Long projectId, Long labelId, LabelRequests.UpdateLabelRequest request) {
        Label label = requireLabelInProject(projectId, labelId);
        label.setName(request.name().trim());
        if (request.color() != null && !request.color().isBlank()) {
            label.setColor(request.color());
        }
        try {
            labelMapper.updateById(label);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "标签名已存在");
        }
        return LabelVO.from(label);
    }

    @Override
    public void delete(Long projectId, Long labelId) {
        requireLabelInProject(projectId, labelId);
        // issue_labels FK ON DELETE CASCADE：绑定随之解除，工作项不受影响
        labelMapper.deleteById(labelId);
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
    }

    private Label requireLabelInProject(Long projectId, Long labelId) {
        Label label = labelMapper.selectById(labelId);
        if (label == null || !label.getProjectId().equals(projectId)) {
            // 跨项目/不存在统一 404（不泄露存在性）
            throw new ResourceNotFoundException("label", labelId);
        }
        return label;
    }
}
