package com.mindman.service.impl;

import com.mindman.dto.AnalysisOverviewVO;
import com.mindman.mapper.AnalysisMapper;
import com.mindman.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据分析服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final AnalysisMapper analysisMapper;

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public AnalysisOverviewVO overview(int days) {
        AnalysisOverviewVO vo = new AnalysisOverviewVO();

        // 概览指标
        vo.setUserTotal(analysisMapper.countUsers());
        vo.setNewUsersToday(analysisMapper.countNewUsersToday());
        vo.setActiveUsers(analysisMapper.countActiveUsers());
        vo.setDiaryTotal(analysisMapper.countDiary());
        vo.setDiaryToday(analysisMapper.countDiaryToday());
        vo.setSessionTotal(analysisMapper.countSession());
        vo.setSessionToday(analysisMapper.countSessionToday());
        vo.setEmotionHealth(round1(analysisMapper.avgEmotionScore() * 2));
        vo.setAvgDuration(round1(analysisMapper.avgSessionDuration()));

        // 趋势日期轴（今天往前 days 天）
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) dates.add(today.minusDays(i));

        // 查询结果按日期建索引
        Map<LocalDate, AnalysisMapper.DayAvgRow> emotionMap = analysisMapper.emotionDaily(days).stream()
                .collect(Collectors.toMap(AnalysisMapper.DayAvgRow::getDate, r -> r, (a, b) -> a));
        Map<LocalDate, AnalysisMapper.DaySessionRow> sessionMap = analysisMapper.sessionDaily(days).stream()
                .collect(Collectors.toMap(AnalysisMapper.DaySessionRow::getDate, r -> r, (a, b) -> a));
        Map<LocalDate, Long> newUserMap = toCountMap(analysisMapper.newUserDaily(days));
        Map<LocalDate, Long> diaryUserMap = toCountMap(analysisMapper.diaryUserDaily(days));
        Map<LocalDate, Long> consultUserMap = toCountMap(analysisMapper.consultUserDaily(days));
        Map<LocalDate, Long> activeUserMap = toCountMap(analysisMapper.activeUserDaily(days));

        List<AnalysisOverviewVO.EmotionTrendItem> emotionTrend = new ArrayList<>();
        List<AnalysisOverviewVO.ConsultActivityItem> consultActivity = new ArrayList<>();
        List<AnalysisOverviewVO.SessionStatItem> sessionStats = new ArrayList<>();
        List<AnalysisOverviewVO.ActivityTrendItem> activityTrend = new ArrayList<>();

        for (LocalDate d : dates) {
            String label = d.format(LABEL_FMT);

            AnalysisMapper.DayAvgRow er = emotionMap.get(d);
            AnalysisOverviewVO.EmotionTrendItem ei = new AnalysisOverviewVO.EmotionTrendItem();
            ei.setDate(label);
            ei.setAvgScore(er == null ? 0d : round1(er.getAvgScore()));
            ei.setCount(er == null ? 0 : er.getCount());
            emotionTrend.add(ei);

            AnalysisMapper.DaySessionRow sr = sessionMap.get(d);
            long sessions = sr == null ? 0 : sr.getSessions();
            long users = sr == null ? 0 : sr.getUsers();

            AnalysisOverviewVO.ConsultActivityItem ci = new AnalysisOverviewVO.ConsultActivityItem();
            ci.setDate(label);
            ci.setSessions(sessions);
            ci.setUsers(users);
            consultActivity.add(ci);

            AnalysisOverviewVO.SessionStatItem si = new AnalysisOverviewVO.SessionStatItem();
            si.setDate(label);
            si.setSessions(sessions);
            sessionStats.add(si);

            AnalysisOverviewVO.ActivityTrendItem ai = new AnalysisOverviewVO.ActivityTrendItem();
            ai.setDate(label);
            ai.setActiveUsers(activeUserMap.getOrDefault(d, 0L));
            ai.setNewUsers(newUserMap.getOrDefault(d, 0L));
            ai.setDiaryUsers(diaryUserMap.getOrDefault(d, 0L));
            ai.setConsultUsers(consultUserMap.getOrDefault(d, 0L));
            activityTrend.add(ai);
        }

        vo.setEmotionTrend(emotionTrend);
        vo.setConsultActivity(consultActivity);
        vo.setSessionStats(sessionStats);
        vo.setActivityTrend(activityTrend);
        return vo;
    }

    private Map<LocalDate, Long> toCountMap(List<AnalysisMapper.DayCountRow> rows) {
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        for (AnalysisMapper.DayCountRow r : rows) {
            map.put(r.getDate(), r.getCount());
        }
        return map;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
