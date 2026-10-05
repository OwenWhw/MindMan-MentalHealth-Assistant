package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_crawl_run")
public class CrawlRunLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String triggerType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer sourceCount;
    private Integer fetchedCount;
    private Integer importedCount;
    private Integer updatedCount;
    private Integer duplicateCount;
    private Integer notModifiedCount;
    private Integer skippedCount;
    private Integer filteredCount;
    private Integer failedCount;
    private String errorSummary;
}
