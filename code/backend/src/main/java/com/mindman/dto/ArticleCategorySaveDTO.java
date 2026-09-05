package com.mindman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类保存 DTO（前端 categories.vue 提交字段）
 */
@Data
public class ArticleCategorySaveDTO {

    /** 仅编辑时使用 */
    private Long categoryId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称最多 64 字")
    private String categoryName;

    @Size(max = 255, message = "描述最多 255 字")
    private String description;

    private Long parentId = 0L;

    private Integer sortOrder = 0;

    private Integer status = 1;
}