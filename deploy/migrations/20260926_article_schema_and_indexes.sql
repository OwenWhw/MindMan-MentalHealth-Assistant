-- MindMan schema reconciliation for existing MySQL 8 databases.
-- Run once with the `mindman` database selected. Back up first.

ALTER TABLE `article`
    ADD COLUMN `source_type` VARCHAR(20) NOT NULL DEFAULT 'local' COMMENT '来源：local/crawled' AFTER `deleted`,
    ADD COLUMN `source_url` VARCHAR(1000) DEFAULT NULL COMMENT '来源链接或生成标识' AFTER `source_type`,
    ADD COLUMN `source_name` VARCHAR(128) DEFAULT NULL COMMENT '来源名称' AFTER `source_url`,
    ADD COLUMN `emotion_tags` VARCHAR(512) DEFAULT NULL COMMENT '情绪标签，逗号分隔' AFTER `source_name`,
    ADD INDEX `idx_article_category_status_publish` (`category_id`, `status`, `deleted`, `publish_time`, `id`),
    ADD INDEX `idx_article_status_publish` (`status`, `deleted`, `publish_time`, `id`),
    ADD INDEX `idx_article_title` (`title`),
    ADD INDEX `idx_article_source_url` (`source_url`(512));

ALTER TABLE `article_category`
    ADD INDEX `idx_category_status_sort` (`status`, `sort_order`, `id`);

ALTER TABLE `chat_session`
    ADD INDEX `idx_session_user_updated` (`user_id`, `updated_at`);

ALTER TABLE `chat_message`
    ADD INDEX `idx_message_session_created` (`session_id`, `created_at`, `id`),
    ADD INDEX `idx_message_user_role_created` (`user_id`, `role`, `created_at`);
