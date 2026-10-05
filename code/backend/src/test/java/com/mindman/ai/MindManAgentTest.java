package com.mindman.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.dto.ArticleVO;
import com.mindman.dto.ChatSendDTO;
import com.mindman.dto.GardenInsightRequest;
import com.mindman.entity.EmotionRecord;
import com.mindman.mapper.EmotionRecordMapper;
import com.mindman.service.AiChatService;
import com.mindman.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MindManAgentTest {

    private final AiChatService aiChatService = mock(AiChatService.class);
    private final EmotionRecordMapper emotionRecordMapper = mock(EmotionRecordMapper.class);
    private final ArticleService articleService = mock(ArticleService.class);
    private final ChatClientRouter chatClientRouter = mock(ChatClientRouter.class);
    private final MindManAgent agent = new MindManAgent(aiChatService, emotionRecordMapper, articleService,
            chatClientRouter, new ObjectMapper());

    @Test
    void usesVerifiedPythonGardenReplyWithoutCallingTheJavaModel() throws Exception {
        PythonGardenAgentClient pythonClient = mock(PythonGardenAgentClient.class);
        ReflectionTestUtils.setField(agent, "pythonGardenAgentClient", pythonClient);
        GardenInsightRequest request = new GardenInsightRequest();
        request.setEmotion("焦虑");
        request.setContent("今天开会时担心自己讲不清楚，后来同事帮我补充了。");
        when(pythonClient.analyze(request)).thenReturn(new ObjectMapper().readTree("""
                {"source":"agent","evidence":"今天开会时担心自己讲不清楚",
                 "observation":"开会时你担心表达不清，后来同事帮你补充了。",
                 "question":"同事补充之后，你的感受有什么变化？",
                 "emotionScore":{"score":2,"evidence":"焦虑","reason":"你选择焦虑作为今天的心情"}}
                """));

        var result = agent.analyzeGardenDraft(request);

        assertTrue("agent".equals(result.getSource()));
        assertTrue(result.getEmotionScore().getScore() == 2);
        verify(pythonClient).analyze(request);
        verifyNoInteractions(chatClientRouter);
    }

    @Test
    void gardenInsightRequiresAnExactQuoteFromTheCurrentNote() {
        GardenInsightRequest request = new GardenInsightRequest();
        request.setEmotion("焦虑");
        request.setContent("今天开会时担心自己讲不清楚，后来同事帮我补充了。");
        when(chatClientRouter.call(anyList(), any())).thenReturn("""
                {"evidence":"今天开会时担心自己讲不清楚","observation":"你写到开会时担心表达不清，也写到同事后来补充了。","question":"同事补充之后，你的感受有什么变化？","scores":{"emotion":{"score":2,"evidence":"焦虑","reason":"你选择焦虑作为今天的心情"},"sleep":{"score":4,"evidence":"同事帮我补充了","reason":"推测睡得不错"},"stress":{"score":null,"evidence":"","reason":""}}}
                """);
        var result = agent.analyzeGardenDraft(request);
        assertTrue("agent".equals(result.getSource()));
        assertTrue(request.getContent().contains(result.getEvidence()));
        assertTrue(result.getEmotionScore().getScore() == 2);
        assertTrue(result.getSleepScore() == null);
        assertTrue(result.getStressScore() == null);

        when(chatClientRouter.call(anyList(), any())).thenReturn("""
                {"evidence":"我被大家批评了","observation":"你被批评了。","question":"后来呢？"}
                """);
        assertTrue("unavailable".equals(agent.analyzeGardenDraft(request).getSource()));
    }

    @Test
    void agentEmotionAnalysisRequiresEvidenceAndNeverReturnsInventedScores() {
        when(chatClientRouter.call(anyList(), any())).thenReturn("""
                {"emotion":"焦虑","evidence":"我很担心明天的汇报","signals":{"pressure":"汇报","anxiety":"我很担心","sleep":""}}
                """);

        var result = agent.analyzeCurrentTurn("我很担心明天的汇报");

        assertTrue("agent".equals(result.getAnalysisSource()));
        assertTrue("焦虑".equals(result.getEmotion()));
        assertTrue("我很担心".equals(result.getCues().get(1).getEvidence()));
        assertTrue(result.getCues().get(2).getEvidence().isBlank());
        assertTrue(result.getStress() == null && result.getAnxiety() == null && result.getSleepRisk() == null);
        assertTrue(result.getEmotionStar() == null && result.getSleepStar() == null && result.getStressStar() == null);
    }

    @Test
    void invalidAgentQuoteFallsBackWithoutTurningNegatedCuesIntoPositiveSignals() {
        when(chatClientRouter.call(anyList(), any())).thenReturn("""
                {"emotion":"焦虑","evidence":"用户明显焦虑","signals":{"pressure":"","anxiety":"用户明显焦虑","sleep":""}}
                """);

        var result = agent.analyzeCurrentTurn("我并不焦虑，也没有睡不着。");

        assertTrue("rules".equals(result.getAnalysisSource()));
        assertTrue("暂不判断".equals(result.getEmotion()));
        assertTrue(result.getEvidence().isBlank());
        assertTrue(result.getCues().stream().allMatch(cue -> cue.getEvidence().isBlank()));
    }

    @Test
    void rulesFallbackKeepsOnlyExplicitCurrentTurnEvidence() {
        when(chatClientRouter.call(anyList(), any())).thenThrow(new IllegalStateException("offline"));

        var result = agent.analyzeCurrentTurn("最近工作压力很大，晚上睡不着，我有点焦虑。");

        assertTrue("rules".equals(result.getAnalysisSource()));
        assertTrue("焦虑".equals(result.getEmotion()));
        assertTrue("压力很大".equals(result.getCues().get(0).getEvidence()));
        assertTrue("焦虑".equals(result.getCues().get(1).getEvidence()));
        assertTrue("睡不着".equals(result.getCues().get(2).getEvidence()));
        assertTrue(result.getEmotionScore() == null);
    }

    @Test
    void rulesFallbackDoesNotCountHedgedOrNegatedCues() {
        when(chatClientRouter.call(anyList(), any())).thenThrow(new IllegalStateException("offline"));

        var result = agent.analyzeCurrentTurn("我没有什么压力，也不太焦虑，昨晚没觉得睡不着。");

        assertTrue("暂不判断".equals(result.getEmotion()));
        assertTrue(result.getEvidence().isBlank());
        assertTrue(result.getCues().stream().allMatch(cue -> cue.getEvidence().isBlank()));
    }

    @Test
    void onlyUsesExplicitlySelectedSourcesAndBuildsBoundedGardenFacts() {
        List<EmotionRecord> records = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            EmotionRecord record = new EmotionRecord();
            record.setRecordDate(LocalDate.now().minusDays(i));
            record.setEmotion(i % 2 == 0 ? "平静" : "焦虑");
            record.setEmotionScore(4);
            record.setSleepScore(i == 0 ? 2 : null);
            record.setStressScore(3);
            record.setRatingSource(i == 0 ? EmotionRecord.RATING_SOURCE_SELF_REPORTED : null);
            record.setTrigger(i < 2 ? "工作安排" : null);
            record.setNote("这是第 " + i + " 条记录");
            records.add(record);
        }
        when(emotionRecordMapper.selectList(any())).thenReturn(records);
        ArticleVO article = new ArticleVO();
        article.setTitle("认识压力反应");
        article.setSummary("留意身体与情绪的变化");
        article.setContent("文章正文");
        when(articleService.publishedReference(77L)).thenReturn(article);

        ChatSendDTO request = new ChatSendDTO();
        request.setContent("请结合我选的内容帮我回顾");
        request.setIncludeGardenContext(true);
        request.setReferenceArticleId(77L);

        String context = agent.buildTurnContext(42L, request, "用户：最近工作比较忙");

        assertTrue(context.contains("基于 14 条可用记录"));
        assertTrue(context.contains("情绪出现次数"));
        assertTrue(context.contains("感受评分均值：4.0/5（1 条有填写）"));
        assertTrue(context.contains("13 条历史记录的评分来源未标注"));
        assertTrue(context.contains("来源未标注，不纳入分析"));
        assertTrue(context.contains("认识压力反应"));
        assertTrue(context.contains("文章正文"));
        assertTrue(context.contains("最近工作比较忙"));
        assertFalse(context.contains("第 14 条记录"));
        verify(emotionRecordMapper).selectList(any());
        verify(articleService).publishedReference(77L);
    }

    @Test
    void doesNotReadGardenOrArticleWithoutUserSelection() {
        ChatSendDTO request = new ChatSendDTO();
        request.setContent("我今天有点累");

        String context = agent.buildTurnContext(42L, request, "");

        assertTrue(context.contains("MindMan 内置陪伴分析 Agent"));
        assertFalse(context.contains("情绪花园近 30 天记录"));
        assertFalse(context.contains("知识阅读中选择的文章"));
        verifyNoInteractions(emotionRecordMapper, articleService);
    }

    @Test
    void marksCrawledArticleAsAnExcerptAndProvidesReadableTextForTranslation() {
        ArticleVO article = new ArticleVO();
        article.setTitle("Why rest matters");
        article.setSourceType("crawled");
        article.setSourceName("APA PsycPORT");
        article.setContent("<p>Short breaks can support attention.</p><ul><li>Pause between tasks.</li></ul>");
        when(articleService.publishedReference(91L)).thenReturn(article);

        ChatSendDTO request = new ChatSendDTO();
        request.setContent("请翻译并解读这篇文章");
        request.setReferenceArticleId(91L);

        String context = agent.buildTurnContext(42L, request, "");

        assertTrue(context.contains("外部订阅源收录的摘要或片段"));
        assertTrue(context.contains("只能翻译和分析本站实际保存的文字"));
        assertTrue(context.contains("来源：APA PsycPORT"));
        assertTrue(context.contains("Short breaks can support attention."));
        assertTrue(context.contains("【文章翻译任务标记】"));
        assertFalse(context.contains("<p>"));
        assertFalse(context.contains("<li>"));
    }

    @Test
    void recommendsOnlyARealPublishedSiteArticleWithoutCallingTheLanguageModel() {
        ArticleVO article = new ArticleVO();
        article.setArticleId(21L);
        article.setTitle("认识焦虑与压力反应");
        article.setSummary("从身体信号开始识别压力，并了解可以尝试的调节方法。");
        when(articleService.recommendPublished(anyList(), eq(1))).thenReturn(List.of(article));

        ChatSendDTO request = new ChatSendDTO();
        request.setContent("我最近有点焦虑，推荐一个文章给我看");

        String reply = agent.respondSync(42L, request, "用户：最近工作压力让我很紧张");

        assertTrue(reply.contains("《认识焦虑与压力反应》"));
        assertTrue(reply.contains("从身体信号开始识别压力"));
        assertTrue(reply.contains("[[mindman-article:21]]"));
        verify(articleService).recommendPublished(List.of("焦虑", "压力", "工作"), 1);
        verifyNoInteractions(aiChatService);
    }

    @Test
    void doesNotInventAnArticleWhenTheSiteHasNoPublishedMatch() {
        when(articleService.recommendPublished(anyList(), eq(1))).thenReturn(List.of());
        ChatSendDTO request = new ChatSendDTO();
        request.setContent("推荐一个文章给我看");

        String reply = agent.respondSync(42L, request, "");

        assertTrue(reply.contains("没有找到可推荐的已发布文章"));
        assertFalse(reply.contains("[假链接"));
        verifyNoInteractions(aiChatService);
    }
}
