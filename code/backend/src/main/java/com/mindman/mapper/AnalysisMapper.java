package com.mindman.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据分析聚合查询 Mapper（使用原生 SQL 做分组统计）。
 */
@Mapper
public interface AnalysisMapper {

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long countUsers();

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND DATE(created_at) = CURDATE()")
    long countNewUsersToday();

    @Select("SELECT COUNT(DISTINCT user_id) FROM (" +
            "SELECT user_id FROM emotion_record WHERE record_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "UNION SELECT user_id FROM chat_session WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)" +
            ") t")
    long countActiveUsers();

    @Select("SELECT COUNT(*) FROM emotion_record")
    long countDiary();

    @Select("SELECT COUNT(*) FROM emotion_record WHERE record_date = CURDATE()")
    long countDiaryToday();

    @Select("SELECT COUNT(*) FROM chat_session")
    long countSession();

    @Select("SELECT COUNT(*) FROM chat_session WHERE DATE(created_at) = CURDATE()")
    long countSessionToday();

    @Select("SELECT COALESCE(AVG(emotion_score), 0) FROM emotion_record")
    double avgEmotionScore();

    @Select("SELECT COALESCE(AVG(TIMESTAMPDIFF(MINUTE, created_at, updated_at)), 0) FROM chat_session")
    double avgSessionDuration();

    @Select("SELECT DATE(record_date) AS date, COALESCE(AVG(emotion_score),0) AS avgScore, COUNT(*) AS cnt " +
            "FROM emotion_record WHERE record_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(record_date)")
    List<DayAvgRow> emotionDaily(@Param("days") int days);

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS sessions, COUNT(DISTINCT user_id) AS users " +
            "FROM chat_session WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at)")
    List<DaySessionRow> sessionDaily(@Param("days") int days);

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS count " +
            "FROM sys_user WHERE deleted = 0 AND DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at)")
    List<DayCountRow> newUserDaily(@Param("days") int days);

    @Select("SELECT DATE(record_date) AS date, COUNT(DISTINCT user_id) AS count " +
            "FROM emotion_record WHERE record_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(record_date)")
    List<DayCountRow> diaryUserDaily(@Param("days") int days);

    @Select("SELECT DATE(created_at) AS date, COUNT(DISTINCT user_id) AS count " +
            "FROM chat_session WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at)")
    List<DayCountRow> consultUserDaily(@Param("days") int days);

    @Select("SELECT DATE(d) AS date, COUNT(DISTINCT user_id) AS count FROM (" +
            "SELECT record_date AS d, user_id FROM emotion_record " +
            "UNION ALL SELECT created_at AS d, user_id FROM chat_session" +
            ") t WHERE DATE(d) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) GROUP BY DATE(d)")
    List<DayCountRow> activeUserDaily(@Param("days") int days);

    // ======================== 行模型 ========================

    class DayAvgRow {
        private LocalDate date;
        private double avgScore;
        private long count;
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public double getAvgScore() { return avgScore; }
        public void setAvgScore(double avgScore) { this.avgScore = avgScore; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    class DaySessionRow {
        private LocalDate date;
        private long sessions;
        private long users;
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public long getSessions() { return sessions; }
        public void setSessions(long sessions) { this.sessions = sessions; }
        public long getUsers() { return users; }
        public void setUsers(long users) { this.users = users; }
    }

    class DayCountRow {
        private LocalDate date;
        private long count;
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
