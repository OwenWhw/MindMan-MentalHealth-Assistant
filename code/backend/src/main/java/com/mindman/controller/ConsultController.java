package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.dto.EmotionAnalysisVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 情绪分析控制器（用户端）。
 *
 * <p>基于用户倾诉内容的关键词分析，输出情绪 / 压力 / 睡眠风险三维评分与建议。
 * 该分析为轻量级规则引擎，可作为大模型情绪判断的降级方案，也可与流式对话并行调用。</p>
 *
 * <h3>接口</h3>
 * <pre>
 * POST /api/consult/emotion/analyze   { "content": "..." } → EmotionAnalysisVO
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/consult")
@RequiredArgsConstructor
@Tag(name = "情绪分析", description = "基于倾诉内容的情绪/压力/睡眠风险分析")
public class ConsultController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> POS   = Arrays.asList("开心", "高兴", "快乐", "愉快", "兴奋", "放松", "平静", "满足", "感恩", "舒心");
    private static final List<String> ANX   = Arrays.asList("焦虑", "紧张", "担心", "害怕", "恐慌", "不安", "恐惧", "心慌", "慌张", "迷茫", "胡思乱想");
    private static final List<String> LOW   = Arrays.asList("难过", "抑郁", "伤心", "痛苦", "绝望", "低落", "委屈", "孤独", "无助", "想哭", "失落", "失望");
    private static final List<String> ANG   = Arrays.asList("愤怒", "生气", "烦躁", "暴躁", "讨厌", "争吵", "吵架", "气死");
    private static final List<String> TIRE  = Arrays.asList("失眠", "睡不着", "熬夜", "疲惫", "累", "困", "无精打采", "没精神", "睡不好", "噩梦", "惊醒");
    private static final List<String> STRESS = Arrays.asList("压力", "忙", "加班", "考试", "deadline", "工作", "学业", "负担", "赶", "扛不住", "撑不住", "崩溃");

    private static final Map<String, String> EMOTION_ICON = new HashMap<>();

    static {
        EMOTION_ICON.put("焦虑", "😰");
        EMOTION_ICON.put("低落", "🌧️");
        EMOTION_ICON.put("愤怒", "😤");
        EMOTION_ICON.put("疲惫", "😮‍💨");
        EMOTION_ICON.put("愉悦", "😄");
        EMOTION_ICON.put("平静", "😌");
    }

    @PostMapping("/emotion/analyze")
    @Operation(summary = "AI 情绪分析（基于倾诉内容）")
    public R<EmotionAnalysisVO> analyzeEmotion(@RequestBody Map<String, String> body) {
        String content = (body != null && body.get("content") != null) ? body.get("content") : "";
        return R.ok(analyze(content));
    }

    // ======================== 分析引擎 ========================

    private EmotionAnalysisVO analyze(String content) {
        if (content == null) content = "";
        String lower = content.toLowerCase();

        int pos = count(lower, POS);
        int anx = count(lower, ANX);
        int low = count(lower, LOW);
        int ang = count(lower, ANG);
        int tire = count(lower, TIRE);
        int str = count(lower, STRESS);

        // 正向情绪词缓和负面指标
        int ease = pos * 7;
        int anxiety = clamp(12 + anx * 26 + (low > 0 ? 12 : 0) - ease);
        int stressScore = clamp(16 + str * 24 + (anx > 0 ? 12 : 0) + (tire > 0 ? 8 : 0) - ease);
        int sleepRisk = clamp(10 + tire * 28 + (anx > 0 ? 8 : 0) - (pos > 0 ? 4 : 0));

        // 无明显情绪词：低基线
        if (anx == 0 && str == 0 && tire == 0 && low == 0 && ang == 0) {
            anxiety = clamp(10 + pos * 2);
            stressScore = clamp(12 + pos * 2);
            sleepRisk = clamp(8 + pos * 2);
        }

        String emotion;
        if (low > 0 && low >= anx && low >= ang && low >= tire) emotion = "低落";
        else if (ang > 0 && ang >= anx && ang >= tire) emotion = "愤怒";
        else if (anx > 0) emotion = "焦虑";
        else if (tire > 0) emotion = "疲惫";
        else if (pos > 0) emotion = "愉悦";
        else emotion = "平静";

        int emotionScore;
        if ("愉悦".equals(emotion)) emotionScore = clamp(84 - Math.max(anxiety, stressScore) / 3);
        else if ("平静".equals(emotion)) emotionScore = clamp(68 - Math.max(anxiety, stressScore) / 4);
        else emotionScore = clamp(92 - Math.max(anxiety, stressScore));

        int emotionStar = clampStar(emotionScore / 20);
        int stressStar = clampStar((100 - stressScore) / 20);
        int sleepStar = clampStar((100 - sleepRisk) / 20);

        EmotionAnalysisVO vo = new EmotionAnalysisVO();
        vo.setEmotion(emotion);
        vo.setEmotionIcon(EMOTION_ICON.getOrDefault(emotion, "🙂"));
        vo.setEmotionScore(emotionScore);
        vo.setEmotionStar(emotionStar);
        vo.setStressStar(stressStar);
        vo.setSleepStar(sleepStar);
        vo.setAnalyzedAt(LocalDateTime.now().format(FMT));
        vo.setStress(stressScore);
        vo.setAnxiety(anxiety);
        vo.setSleepRisk(sleepRisk);
        vo.setStressLevel(level(stressScore));
        vo.setAnxietyLevel(level(anxiety));
        vo.setSleepLevel(level(sleepRisk));
        vo.setSuggestions(buildSuggestions(emotion, anxiety, stressScore, sleepRisk));
        return vo;
    }

    private int count(String text, List<String> words) {
        int c = 0;
        for (String w : words) if (text.contains(w)) c++;
        return c;
    }

    private int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    private int clampStar(int v) { return Math.max(1, Math.min(5, v)); }

    private String level(int v) { return v >= 66 ? "高" : v >= 33 ? "中" : "低"; }

    private List<String> buildSuggestions(String emotion, int anxiety, int stress, int sleep) {
        List<String> tips = new ArrayList<>();
        if (anxiety >= 50) tips.add("尝试 4-7-8 呼吸法：吸气 4 秒、屏息 7 秒、呼气 8 秒，重复几次可缓解紧张。");
        if (sleep >= 50) tips.add("建立规律作息，睡前 1 小时减少屏幕使用，有助于改善睡眠。");
        if (stress >= 50) tips.add("把大任务拆成小步骤，给自己留出休息的空白时间。");
        if ("低落".equals(emotion)) tips.add("允许自己有低落的情绪，找信任的人聊聊会好受一些。");
        if ("愤怒".equals(emotion)) tips.add("先暂停一下，深呼吸或暂时离开现场，避免冲动决策。");
        if (tips.isEmpty()) tips.add("当前状态不错，记录下让你开心的小事，巩固积极心态。");
        return tips;
    }
}
