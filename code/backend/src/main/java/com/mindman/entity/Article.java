package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章
 */
@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private Long categoryId;
    private String cover;
    private String summary;
    private String content;
    private String tags;           // JSON 数组字符串
    private String author;
    @TableField("`reads`")
    private Long reads;
    private Integer status;        // 1 已发布 0 草稿

    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 内容来源：local 本站原创 / crawled 外部抓取 */
    private String sourceType;

    /** 爬取原文链接（仅 crawled 时有值） */
    private String sourceUrl;

    /** 来源网站名称（如：KnowYourself / 知乎专栏 / 简书 等） */
    private String sourceName;

    /** 情绪标签，英文逗号拼接，用于个性化推荐匹配 */
    private String emotionTags;
}
