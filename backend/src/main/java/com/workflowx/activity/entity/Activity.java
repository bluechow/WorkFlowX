package com.workflowx.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 项目活动（V18）：真实业务事件，由各 Service 事务内写入。 */
@Data
@TableName("activities")
public class Activity {

    public enum Action {
        CREATE, UPDATE, TRANSITION, ASSIGN, COMMENT, ATTACHMENT, LABEL, LINK, DUE
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 可空：项目级事件无工作项 */
    private Long issueId;

    private Long actorId;

    private Action action;

    private String target;

    private String summary;

    private LocalDateTime createdAt;
}
