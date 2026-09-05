package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪记录
 */
@Data
@TableName("emotion_record")
public class EmotionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String emotion;       // 开心/焦虑/平静...
    private String emotionIcon;   // 表情
    private Integer emotionScore; // 情绪评分 1-5
    private String note;          // 备注 / 日记内容
    private Integer sleepScore;   // 睡眠质量评分 1-5（前端情绪花园/日志）
    private Integer stressScore;  // 压力水平评分 1-5
    @TableField("`trigger`")       // trigger 是 MySQL 保留字，需用反引号转义
    private String trigger;       // 情绪触发因素
    private LocalDate recordDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}