package com.mindman.dto;

import java.util.List;

public record CrawlerStatusVO(
        boolean enabled,
        boolean running,
        String cron,
        String zone,
        List<CrawlerFeedInfo> feeds,
        CrawlRunResult lastRun,
        List<CrawlRunResult> recentRuns
) {}
