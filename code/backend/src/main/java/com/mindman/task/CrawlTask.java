package com.mindman.task;

import com.mindman.service.RealtimeCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 实时心理文章定时爬取任务。
 *
 * <p>默认 <strong>每天凌晨 04:30</strong> 执行一次，每次尝试入库 3-5 篇。</p>
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

    /** 每天 04:30 跑一次。生产可改为 cron 表达式 */
    @Scheduled(cron = "0 30 4 * * ?")
    public void dailyCrawl() {
        try {
            int n = crawlerService.crawlOnce(4);
            log.info("DailyCrawlTask done. saved={}", n);
        } catch (Exception e) {
            log.error("DailyCrawlTask error: {}", e.getMessage(), e);
        }
    }
}
