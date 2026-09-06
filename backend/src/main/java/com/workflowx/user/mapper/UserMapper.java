package com.workflowx.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问（P2-02）。
 * CRUD 与分页能力由 BaseMapper + MybatisPlusInterceptor 分页插件提供，不重复实现；
 * 查询条件（过滤/排序）由调用方（P2-03 起 Service）通过 LambdaQueryWrapper 构造，Mapper 保持极简。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
