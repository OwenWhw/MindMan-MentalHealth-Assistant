package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板。
 *
 * <p>按 {@code scene}（场景键）管理各 AI 功能的系统提示词，支持 {@code {变量}} 占位符渲染；
 * 同一场景下仅允许一条 {@code enabled=1} 的生效模板，查询时按 {@code updated_at} 取最新。
 */
@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景键：chat_system=AI咨询 / quote_gen=语录生成 / article_recommend=文章推荐 */
    private String scene;

    /** 模板名称（管理端展示用） */
    private String name;

    /** 模板内容，支持 {变量名} 占位符 */
    private String template;

    /** 变量说明 JSON：[{"key":"username","desc":"用户昵称"}, ...] */
    private String variables;

    /** 备注 */
    private String remark;

    /** 1启用 0停用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
