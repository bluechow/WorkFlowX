package com.workflowx.issue.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 工作项-标签绑定（V17；复合主键，仅作删除/查询载体）。 */
@Data
@TableName("issue_labels")
public class IssueLabel {

    private Long issueId;

    private Long labelId;
}
