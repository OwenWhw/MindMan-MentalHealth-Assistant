package com.mindman.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章分类响应 VO（前端约定字段名）
 *
 * <p>前端 categories.vue / ArticlesView.vue 期望的字段：
 * <ul>
 *   <li>categoryId - 主键别名（前端用 categoryId 而非 id）</li>
 *   <li>categoryName - 分类名称</li>
 *   <li>description - 分类描述</li>
 *   <li>sortOrder - 排序值</li>
 *   <li>articleCount - 该分类下的文章数（联表统计）</li>
 *   <li>children - 子分类（树形）</li>
 * </ul>
 */
@Data
public class ArticleCategoryVO {

    private Long categoryId;
    private String categoryName;
    private String description;
    private Integer sortOrder;
    private Integer status;
    private Long articleCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private List<ArticleCategoryVO> children = new ArrayList<>();
}