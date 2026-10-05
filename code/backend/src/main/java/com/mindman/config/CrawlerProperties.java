package com.mindman.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mindman.crawler")
public class CrawlerProperties {
    private boolean enabled = true;
    private String cron = "0 30 4 * * ?";
    private String zone = "Asia/Shanghai";
    private int dailyLimit = 20;
    private int perFeedLimit = 12;
    private int maxAgeDays = 180;
    private int requestTimeoutSeconds = 15;
    private int minCheckIntervalHours = 20;
}
