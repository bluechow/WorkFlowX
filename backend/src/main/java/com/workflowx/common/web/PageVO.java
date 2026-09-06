package com.workflowx.common.web;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 统一分页响应结构（docs/api/api-conventions.md §5：list/total/page/size）。
 */
public record PageVO<T>(List<T> list, long total, long page, long size) {

    public static <E> PageVO<E> of(IPage<E> page) {
        return new PageVO<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
