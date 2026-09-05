package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.dto.EmotionInsightVO;
import com.mindman.service.EmotionInsightService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪洞察 Controller。
 */
@RestController
@RequestMapping("/api/emotion/insight")
@Tag(name = "情绪洞察")
@RequiredArgsConstructor
public class EmotionInsightController {

    private final EmotionInsightService insightService;

    @GetMapping("/this-week")
    public R<EmotionInsightVO> thisWeek() {
        Long uid = LoginUser.get();
        return R.ok(insightService.thisWeekInsight(uid));
    }
}
