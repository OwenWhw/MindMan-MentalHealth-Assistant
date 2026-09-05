package com.mindman.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 文章分页查询条件
 *
 * <h3>使用示例</h3>
 * <pre>
 * GET /api/articles?page=1&pageSize=10&categoryId=2&keyword=正念&status=1
 * </pre>
 *
 * 所有参数都可空，service 层按非空条件组合查询。
 */
@Data
public class ArticleQueryDTO {

    @Min(value = 1, message = "页码不能小于 1")
    private Long page = 1L;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能大于 100")
    private Long pageSize = 10L;

    /** 分类 ID（可选） */
    private Long categoryId;

    /** 标题模糊查询（前端用 title 字段，与 keyword 等价） */
    private String title;

    /** 关键字（兼容老接口） */
    private String keyword;

    /** 1=已发布 0=草稿（可选） */
    private Integer status;
}