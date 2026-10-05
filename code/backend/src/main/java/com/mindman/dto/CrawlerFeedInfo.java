package com.mindman.dto;

import java.time.LocalDateTime;

public record CrawlerFeedInfo(String name, String feedUrl, boolean enabled, LocalDateTime lastSuccessfulAt) {}
