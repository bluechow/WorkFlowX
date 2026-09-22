package com.workflowx.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 通知数据访问（P9-02）：列表查询由 BaseMapper+Wrapper 构造；批量已读走条件 UPDATE（ADR-019）。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 单条标记已读：仅当属于 recipient 且未读时生效（ownership+幂等一并收敛在 SQL）。 */
    @Update("UPDATE notifications SET is_read = 1, read_at = #{now} "
            + "WHERE id = #{id} AND recipient_id = #{recipientId} AND is_read = 0")
    int markRead(@Param("id") Long id,
                 @Param("recipientId") Long recipientId,
                 @Param("now") LocalDateTime now);

    /** 全部已读：数据库条件更新，只影响当前用户未读通知。 */
    @Update("UPDATE notifications SET is_read = 1, read_at = #{now} "
            + "WHERE recipient_id = #{recipientId} AND is_read = 0")
    int markAllRead(@Param("recipientId") Long recipientId,
                    @Param("now") LocalDateTime now);
}
