package com.workflowx.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.issue.entity.Label;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LabelMapper extends BaseMapper<Label> {
}
