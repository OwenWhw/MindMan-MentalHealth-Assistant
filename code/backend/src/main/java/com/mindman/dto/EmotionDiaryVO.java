package com.mindman.dto;

import com.mindman.entity.EmotionRecord;
import com.mindman.entity.User;

import java.time.format.DateTimeFormatter;

/**
 * 情绪日志视图对象（管理端）。
 *
 * <p>字段严格对齐前端 {@code diaries.vue} 读取的日志字段：
 * {@code diaryId / sessionId / userId / userName / avatar / recordDate / emotionScore /
 * sleepScore / stressScore / trigger / content / emotion}</p>
 *
 * <p>{@code userName / avatar} 来自关联用户表（昵称优先，缺省回退用户名）；
 * {@code content} 取自 {@code note}；{@code recordDate} 格式化为 yyyy-MM-dd。</p>
 */
public class EmotionDiaryVO {

    private Long diaryId;
    private Long sessionId;     // 当前未关联会话，预留字段
    private Long userId;
    private String userName;
    private String avatar;
    private String recordDate;  // yyyy-MM-dd
    private Integer emotionScore;
    private Integer sleepScore;
    private Integer stressScore;
    private String trigger;
    private String content;
    private String emotion;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static EmotionDiaryVO from(EmotionRecord r, User u) {
        if (r == null) return null;
        EmotionDiaryVO vo = new EmotionDiaryVO();
        vo.setDiaryId(r.getId());
        vo.setSessionId(null);
        vo.setUserId(r.getUserId());
        if (u != null) {
            vo.setUserName(u.getNickname() != null ? u.getNickname() : u.getUsername());
            vo.setAvatar(u.getAvatar());
        }
        if (r.getRecordDate() != null) vo.setRecordDate(r.getRecordDate().format(DATE_FMT));
        vo.setEmotionScore(r.getEmotionScore());
        vo.setSleepScore(r.getSleepScore());
        vo.setStressScore(r.getStressScore());
        vo.setTrigger(r.getTrigger());
        vo.setContent(r.getNote());
        vo.setEmotion(r.getEmotion());
        return vo;
    }

    public Long getDiaryId() { return diaryId; }
    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String recordDate) { this.recordDate = recordDate; }
    public Integer getEmotionScore() { return emotionScore; }
    public void setEmotionScore(Integer emotionScore) { this.emotionScore = emotionScore; }
    public Integer getSleepScore() { return sleepScore; }
    public void setSleepScore(Integer sleepScore) { this.sleepScore = sleepScore; }
    public Integer getStressScore() { return stressScore; }
    public void setStressScore(Integer stressScore) { this.stressScore = stressScore; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
}
