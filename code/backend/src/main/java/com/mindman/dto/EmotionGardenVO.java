package com.mindman.dto;

import com.mindman.entity.EmotionRecord;

import java.time.format.DateTimeFormatter;

/**
 * 情绪花园视图对象（用户端）。
 *
 * <p>字段严格对齐前端 {@code GardenView.vue} 读取的花朵字段：
 * {@code flowerId / emotion / content / emotionScore / sleepScore / stressScore / trigger / date / createdAt}</p>
 *
 * <p>其中 {@code content} 取自 {@code note}，{@code date} 取自 {@code recordDate}（格式 yyyy-MM-dd），
 * 该字符串格式可直接用于前端按日期的字符串比较与排序。</p>
 */
public class EmotionGardenVO {

    private Long flowerId;
    private String emotion;
    private String content;
    private Integer emotionScore;
    private Integer sleepScore;
    private Integer stressScore;
    private String trigger;    // 情绪触发因素
    private String date;       // yyyy-MM-dd（对应 recordDate）
    private String createdAt;  // yyyy-MM-dd HH:mm:ss

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static EmotionGardenVO from(EmotionRecord r) {
        if (r == null) return null;
        EmotionGardenVO vo = new EmotionGardenVO();
        vo.setFlowerId(r.getId());
        vo.setEmotion(r.getEmotion());
        vo.setContent(r.getNote());
        vo.setEmotionScore(r.getEmotionScore());
        vo.setSleepScore(r.getSleepScore());
        vo.setStressScore(r.getStressScore());
        vo.setTrigger(r.getTrigger());
        if (r.getRecordDate() != null) vo.setDate(r.getRecordDate().format(DATE_FMT));
        if (r.getCreatedAt() != null) vo.setCreatedAt(r.getCreatedAt().format(DT_FMT));
        return vo;
    }

    public Long getFlowerId() { return flowerId; }
    public void setFlowerId(Long flowerId) { this.flowerId = flowerId; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getEmotionScore() { return emotionScore; }
    public void setEmotionScore(Integer emotionScore) { this.emotionScore = emotionScore; }
    public Integer getSleepScore() { return sleepScore; }
    public void setSleepScore(Integer sleepScore) { this.sleepScore = sleepScore; }
    public Integer getStressScore() { return stressScore; }
    public void setStressScore(Integer stressScore) { this.stressScore = stressScore; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
