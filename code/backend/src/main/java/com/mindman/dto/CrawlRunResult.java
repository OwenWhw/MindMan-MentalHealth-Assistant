package com.mindman.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CrawlRunResult(
        Long id,
        String triggerType,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int sourceCount,
        int fetchedCount,
        int importedCount,
        int updatedCount,
        int duplicateCount,
        int notModifiedCount,
        int skippedCount,
        int filteredCount,
        int failedCount,
        String errorSummary,
        List<String> errors
) {}
