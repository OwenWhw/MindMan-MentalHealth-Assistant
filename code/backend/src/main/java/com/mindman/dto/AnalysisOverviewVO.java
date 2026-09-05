package com.mindman.dto;

import lombok.Data;

import java.util.List;

/**
 * 平台综合分析视图对象（管理端数据分析页）。
 *
 * <p>字段对齐前端 {@code backend/index.vue}：</p>
 * <ul>
 *   <li>概览卡片：userTotal / activeUsers / newUsersToday / diaryTotal / diaryToday /
 *       sessionTotal / sessionToday / emotionHealth(0-10) / avgDuration(分钟)</li>
 *   <li>图表：emotionTrend[{date,avgScore,count}] / consultActivity[{date,sessions,users}] /
 *       sessionStats[{date,sessions}] / activityTrend[{date,activeUsers,newUsers,diaryUsers,consultUsers}]</li>
 * </ul>
 */
@Data
public class AnalysisOverviewVO {

    private long userTotal;
    private long activeUsers;
    private long newUsersToday;
    private long diaryTotal;
    private long diaryToday;
    private long sessionTotal;
    private long sessionToday;
    private Double emotionHealth;   // 0-10
    private Double avgDuration;     // 分钟

    private List<EmotionTrendItem> emotionTrend;
    private List<ConsultActivityItem> consultActivity;
    private List<SessionStatItem> sessionStats;
    private List<ActivityTrendItem> activityTrend;

    @Data
    public static class EmotionTrendItem {
        private String date;
        private Double avgScore;
        private long count;
    }

    @Data
    public static class ConsultActivityItem {
        private String date;
        private long sessions;
        private long users;
    }

    @Data
    public static class SessionStatItem {
        private String date;
        private long sessions;
    }

    @Data
    public static class ActivityTrendItem {
        private String date;
        private long activeUsers;
        private long newUsers;
        private long diaryUsers;
        private long consultUsers;
    }
}
