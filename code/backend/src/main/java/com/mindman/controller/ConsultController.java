package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.ai.MindManAgent;
import com.mindman.dto.EmotionAnalysisVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 情绪分析控制器（用户端）。
 *
 * <p>调用 MindMan 内置 Agent 提取本轮原话中的情绪线索；Agent 不可用时使用保守规则兜底。
 * 对话文本不是经过验证的量表，因此此接口不再生成百分比或星级分数。</p>
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

    private final MindManAgent mindManAgent;

    @PostMapping("/emotion/analyze")
    @Operation(summary = "分析本轮情绪线索（Agent + 原话依据）")
    public R<EmotionAnalysisVO> analyzeEmotion(@RequestBody Map<String, String> body) {
        String content = (body != null && body.get("content") != null) ? body.get("content") : "";
        return R.ok(mindManAgent.analyzeCurrentTurn(content));
    }
}
