package com.workflowx.issue.vo;

import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueLink;

/** 工作项关联视图对象（V17）：另一端工作项摘要 + 关联类型与方向。 */
public record IssueLinkVO(
        Long linkId,
        Long otherIssueId,
        Long otherIssueNo,
        String otherTitle,
        String otherType,
        String otherStatus,
        String linkType,
        /** OUTGOING=我指向他人 / INCOMING=他人指向我 */
        String direction) {

    public static IssueLinkVO outgoing(IssueLink link, Issue other) {
        return new IssueLinkVO(link.getId(), other.getId(), other.getIssueNo(), other.getTitle(),
                other.getType().name(), other.getStatus().name(), link.getLinkType().name(), "OUTGOING");
    }

    public static IssueLinkVO incoming(IssueLink link, Issue other) {
        return new IssueLinkVO(link.getId(), other.getId(), other.getIssueNo(), other.getTitle(),
                other.getType().name(), other.getStatus().name(), link.getLinkType().name(), "INCOMING");
    }
}
