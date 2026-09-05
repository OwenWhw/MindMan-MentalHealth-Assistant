package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.dto.QuoteVO;
import com.mindman.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日一句 / 语录接口。
 */
@RestController
@RequestMapping("/api/quote")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    /**
     * 随机获取一条语录（可来自外部一言 API 或本地兜底）。
     */
    @GetMapping("/random")
    public R<QuoteVO> random() {
        return R.ok(quoteService.randomQuote());
    }
}
