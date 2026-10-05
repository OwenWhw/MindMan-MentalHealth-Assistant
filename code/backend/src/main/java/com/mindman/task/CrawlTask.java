package com.mindman.task;

import com.mindman.config.CrawlerProperties;
import com.mindman.service.RealtimeCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 实时心理文章定时爬取任务。
 *
 * <p>默认每天凌晨 04:30 同步官方 RSS，每个来源每天最多检查一次。</p>
 *
 * <p>可调参（application-dev.yml / -D）：</p>
 * <ul>
 *   <li>{@code mindman.crawler.daily-limit}：单次爬取条数（默认 4）</li>
 * </ul>
 *
 * <p>手动触发：后台调用 {@code POST /api/admin/crawler/run}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlTask {

    private final RealtimeCrawlerService crawlerService;
    private final CrawlerProperties properties;

    /** 每天 04:30 跑一次。生产可改为 cron 表达式 */
    @Scheduled(cron = "${mindman.crawler.cron:0 30 4 * * ?}", zone = "${mindman.crawler.zone:Asia/Shanghai}")
    public void dailyCrawl() {
        if (!properties.isEnabled()) {
            log.info("Daily article feed sync is disabled");
            return;
        }
        try {
            var result = crawlerService.crawlDetailed(properties.getDailyLimit(), "scheduled");
            log.info("Daily article feed sync done. status={} imported={} updated={} failed={}",
                    result.status(), result.importedCount(), result.updatedCount(), result.failedCount());
        } catch (Exception e) {
            log.error("DailyCrawlTask error: {}", e.getMessage(), e);
        }
    }
}
