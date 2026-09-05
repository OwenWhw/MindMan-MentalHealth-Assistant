package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文章分类
 */
@Data
@TableName("article_category")
public class ArticleCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名（数据库字段 name，前端展示用 categoryName 别名） */
    private String name;
    private String description;
    private Long parentId;
    private Integer sortOrder;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}