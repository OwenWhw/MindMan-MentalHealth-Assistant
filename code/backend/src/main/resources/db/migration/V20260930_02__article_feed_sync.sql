-- Persistent feed validators and run history for the official APA RSS sync.
CREATE TABLE IF NOT EXISTS `article_crawl_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `trigger_type` VARCHAR(16) NOT NULL COMMENT 'manual/scheduled',
    `status` VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED/SKIPPED/DISABLED',
    `started_at` DATETIME NOT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `source_count` INT NOT NULL DEFAULT 0,
    `fetched_count` INT NOT NULL DEFAULT 0,
    `imported_count` INT NOT NULL DEFAULT 0,
    `updated_count` INT NOT NULL DEFAULT 0,
    `duplicate_count` INT NOT NULL DEFAULT 0,
    `not_modified_count` INT NOT NULL DEFAULT 0,
    `skipped_count` INT NOT NULL DEFAULT 0,
    `filtered_count` INT NOT NULL DEFAULT 0,
    `failed_count` INT NOT NULL DEFAULT 0,
    `error_summary` VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_article_crawl_run_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章 RSS 同步记录';

CREATE TABLE IF NOT EXISTS `article_crawl_feed_state` (
    `feed_url` VARCHAR(512) NOT NULL,
    `feed_name` VARCHAR(128) NOT NULL,
    `etag` VARCHAR(512) DEFAULT NULL,
    `last_modified` VARCHAR(128) DEFAULT NULL,
    `last_checked_at` DATETIME DEFAULT NULL,
    `last_successful_at` DATETIME DEFAULT NULL,
    `last_item_count` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`feed_url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RSS 来源同步状态';

-- Keep historical generated placeholders for administrators, but stop presenting them as real articles.
UPDATE `article`
SET `status` = 0, `updated_at` = NOW()
WHERE `source_type` = 'crawled'
  AND `source_url` LIKE 'ai://generated/%'
  AND `status` = 1;
