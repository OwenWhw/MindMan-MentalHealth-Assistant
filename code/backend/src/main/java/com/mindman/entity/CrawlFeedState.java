package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_crawl_feed_state")
public class CrawlFeedState {
    @TableId(type = IdType.INPUT)
    private String feedUrl;
    private String feedName;
    private String etag;
    private String lastModified;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime lastSuccessfulAt;
    private Integer lastItemCount;
}
