package com.mindman.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 情绪记录保存（种花 / 编辑）请求体。
 *
 * <p>前端 {@code GardenView} 表单提交字段：emotion / content / emotionScore / sleepScore / stressScore / trigger</p>
 */
@Data
public class EmotionRecordSaveDTO {

    /** 情绪类型（开心/焦虑/平静...），必填 */
    @NotBlank(message = "请选择一种心情")
    private String emotion;

    /** 心情记录内容（日记），最长 255 字 */
    @Size(max = 255, message = "心情记录不能超过 255 字")
    private String content;

    /** 情绪评分 1-5 */
    @Min(1)
    @Max(5)
    private Integer emotionScore;

    /** 睡眠质量评分 1-5 */
    @Min(1)
    @Max(5)
    private Integer sleepScore;

    /** 压力水平评分 1-5 */
    @Min(1)
    @Max(5)
    private Integer stressScore;

    /** 情绪触发因素，最长 64 字 */
    @Size(max = 64, message = "触发因素过长")
    private String trigger;
}
