package com.mindman.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindman.dto.QuoteVO;
import com.mindman.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * 语录服务实现。
 *
 * <p>外部来源：hitokoto.cn 一言 API（https://v1.hitokoto.cn/?encode=json）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    private final WebClient.Builder webClientBuilder;

    private static final String HITOKOTO_URL = "https://v1.hitokoto.cn/?encode=json";

    private static final List<QuoteVO> FALLBACK_QUOTES = List.of(
            new QuoteVO("不必匆忙，不必火花四溅，不必成为别人，只需做自己。",
                    "You don't have to be on fire. Just be yourself.", "弗吉尼亚·伍尔夫", null),
            new QuoteVO("你担心的事情，百分之九十都不会发生。",
                    "90% of the things you worry about will never happen.", "佚名", null),
            new QuoteVO("慢慢来，比较快。",
                    "Slow down, and you will get there faster.", "佚名", null),
            new QuoteVO("每一个不曾起舞的日子，都是对生命的辜负。",
                    "And those who were seen dancing were thought to be insane by those who could not hear the music.", "尼采", null),
            new QuoteVO("万物皆有裂痕，那是光照进来的地方。",
                    "There is a crack in everything, that's how the light gets in.", "莱昂纳德·科恩", null),
            new QuoteVO("做自己，因为别人都有人做了。",
                    "Be yourself; everyone else is already taken.", "奥斯卡·王尔德", null)
    );

    @Override
    public QuoteVO randomQuote() {
        try {
            JsonNode root = webClientBuilder.build()
                    .get()
                    .uri(HITOKOTO_URL)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (root == null || !root.has("hitokoto")) {
                log.warn("hitokoto 返回空数据，使用兜底语录");
                return fallback();
            }

            String content = root.path("hitokoto").asText("").trim();
            String from = root.path("from").asText("").trim();
            String fromWho = root.path("from_who").asText("").trim();

            if (content.isEmpty()) {
                return fallback();
            }

            String author = !fromWho.isEmpty() ? fromWho : (!from.isEmpty() ? from : "佚名");
            return new QuoteVO(content, null, author, from.isEmpty() ? null : from);
        } catch (WebClientResponseException e) {
            log.warn("hitokoto 响应异常: {} {}", e.getStatusCode(), e.getMessage());
            return fallback();
        } catch (Exception e) {
            log.warn("获取外部语录失败: {}", e.getMessage());
            return fallback();
        }
    }

    private QuoteVO fallback() {
        return FALLBACK_QUOTES.get(new Random().nextInt(FALLBACK_QUOTES.size()));
    }
}
