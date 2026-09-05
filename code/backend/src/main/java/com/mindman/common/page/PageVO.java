package com.mindman.common.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 分页响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    private long total;
    private long page;
    private long pageSize;
    private long pages;
    private List<T> list;

    public static <T> PageVO<T> of(long total, long page, long pageSize, List<T> list) {
        long pages = (total + pageSize - 1) / pageSize;
        return new PageVO<>(total, page, pageSize, pages, list);
    }
}