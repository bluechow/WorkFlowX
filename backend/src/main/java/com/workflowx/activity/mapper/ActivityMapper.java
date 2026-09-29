package com.workflowx.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
