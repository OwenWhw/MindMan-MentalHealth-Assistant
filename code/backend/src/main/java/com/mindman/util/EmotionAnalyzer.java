package com.mindman.util;

/**
 * 情绪分析工具类
 * 
 * <p>基于关键词匹配的情绪识别算法，用于快速判断用户情绪状态。</p>
 */
public class EmotionAnalyzer {

    /**
     * 分析内容中的情绪类型
     * 
     * @param content 用户输入内容
     * @return 情绪类型：焦虑/低落/愤怒/疲惫/愉悦/平静
     */
    public static String analyze(String content) {
        if (content == null || content.isBlank()) {
            return "平静";
        }

        String lower = content.toLowerCase();

        // 负面情绪优先级判断
        if (hasKeywords(lower, "焦虑", "紧张", "担心", "害怕", "恐慌")) {
            return "焦虑";
        }
        
        if (hasKeywords(lower, "难过", "抑郁", "伤心", "痛苦", "绝望", "低落", "委屈", "孤独", "无助", "想哭", "失落", "失望")) {
            return "低落";
        }
        
        if (hasKeywords(lower, "愤怒", "生气", "烦躁", "暴躁", "讨厌", "争吵", "吵架", "气死")) {
            return "愤怒";
        }
        
        if (hasKeywords(lower, "失眠", "睡不着", "熬夜", "疲惫", "累", "困", "无精打采", "没精神", "睡不好", "噩梦", "惊醒")) {
            return "疲惫";
        }

        // 正面情绪判断
        if (hasKeywords(lower, "开心", "高兴", "快乐", "愉快", "兴奋", "放松", "平静", "满足", "感恩", "舒心")) {
            return "愉悦";
        }

        return "平静";
    }

    /**
     * 检查是否包含多个关键词中的任意一个
     */
    private static boolean hasKeywords(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
