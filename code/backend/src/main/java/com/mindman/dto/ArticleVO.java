package com.mindman.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章响应 VO（前端约定字段名）
 *
 * <p>前端 articles.vue / ArticlesView.vue 期望的字段：
 * <ul>
 *   <li>articleId - 主键别名</li>
 *   <li>categoryId / categoryName - 分类ID 与名称</li>
 *   <li>title / summary / content / cover / tags / author</li>
 *   <li>reads / status / publishTime</li>
 * </ul>
 */
@Data
public class ArticleVO {

    private Long articleId;
    private String title;
    private Long categoryId;
    private String categoryName;
    private String cover;
    private String summary;
    private String content;
    private String tags;
    private String author;
    private Long reads;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 内容来源：local 本站原创 / crawled 外部抓取 */
    private String sourceType;

    /** 原始来源链接（仅 crawled） */
    private String sourceUrl;

    /** 来源网站名称 */
    private String sourceName;
}
