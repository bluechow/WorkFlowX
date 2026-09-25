package com.workflowx.testcase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例目录实体，映射 V15 表 test_case_directories（ADR-022）。
 * 自引用树：parentId=NULL 为根目录；目录删除级联删除子目录。
 */
@Data
@TableName("test_case_directories")
public class TestCaseDirectory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 父目录 id；NULL=根目录 */
    private Long parentId;

    private String name;

    /** 创建者，服务端绑定，不接受客户端传入 */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
