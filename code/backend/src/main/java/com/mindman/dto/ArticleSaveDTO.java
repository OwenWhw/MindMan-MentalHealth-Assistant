package com.mindman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章保存 DTO（前端 ArticleFormDialog.vue 提交字段）
 */
@Data
public class ArticleSaveDTO {

    /** 编辑时携带 */
    private Long articleId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题最多 255 字")
    private String title;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    @Size(max = 255, message = "封面 URL 过长")
    private String cover;

    @Size(max = 512, message = "摘要最多 512 字")
    private String summary;

    private String content;

    /** 标签 JSON 数组字符串 */
    private String tags;

    @Size(max = 64, message = "作者名最多 64 字")
    private String author;

    private Integer status;
}