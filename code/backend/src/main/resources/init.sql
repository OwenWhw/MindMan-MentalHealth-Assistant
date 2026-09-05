-- =============================================
-- MindMan 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS `mindman` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mindman`;

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `role`        VARCHAR(20)  DEFAULT 'user' COMMENT 'admin/user',
    `status`      TINYINT      DEFAULT 1 COMMENT '1正常 0禁用',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文章分类表
CREATE TABLE IF NOT EXISTS `article_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(64)  NOT NULL COMMENT '分类名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    `parent_id`   BIGINT       DEFAULT 0 COMMENT '父级ID，0=顶级',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序值，值小在前',
    `status`      TINYINT      DEFAULT 1 COMMENT '1启用 0停用',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类';

-- 文章表
CREATE TABLE IF NOT EXISTS `article` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `title`        VARCHAR(255) NOT NULL COMMENT '标题',
    `category_id`  BIGINT       DEFAULT NULL COMMENT '分类ID',
    `cover`        VARCHAR(255) DEFAULT NULL COMMENT '封面图',
    `summary`      VARCHAR(512) DEFAULT NULL COMMENT '摘要',
    `content`      TEXT         DEFAULT NULL COMMENT '正文',
    `tags`         VARCHAR(512) DEFAULT NULL COMMENT '标签JSON',
    `author`       VARCHAR(64)  DEFAULT 'MindMan' COMMENT '作者',
    `reads`        BIGINT       DEFAULT 0 COMMENT '阅读量',
    `status`       TINYINT      DEFAULT 1 COMMENT '1已发布 0草稿',
    `publish_time` DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 情绪记录表
CREATE TABLE IF NOT EXISTS `emotion_record` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT      NOT NULL COMMENT '用户ID',
    `emotion`       VARCHAR(32) NOT NULL COMMENT '情绪类型',
    `emotion_icon`  VARCHAR(8)  DEFAULT NULL COMMENT '表情',
    `emotion_score` INT         DEFAULT 3 COMMENT '1-5评分',
    `note`          VARCHAR(255) DEFAULT NULL COMMENT '备注/日记内容',
    `sleep_score`   INT         DEFAULT 3 COMMENT '睡眠质量1-5',
    `stress_score`  INT         DEFAULT 3 COMMENT '压力水平1-5',
    `trigger`       VARCHAR(64) DEFAULT NULL COMMENT '情绪触发因素',
    `record_date`   DATE        NOT NULL COMMENT '记录日期',
    `created_at`    DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪记录';

-- 会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `title`      VARCHAR(255) DEFAULT '新的咨询',
    `status`     TINYINT      DEFAULT 1 COMMENT '1进行中 2已结束',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话';

-- 消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT       NOT NULL,
    `user_id`    BIGINT       NOT NULL,
    `role`       VARCHAR(16)  NOT NULL COMMENT 'user/assistant',
    `content`    TEXT         NOT NULL,
    `emotion`    VARCHAR(128) DEFAULT NULL COMMENT 'AI情绪分析结果',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

-- 初始管理员
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`) VALUES
('admin', '$2b$10$aVgNKWrIrkuWNBgwEbrx7u3KwUQc3XF438doNL/3BDU6tdXEVvi1e', '超级管理员', 'admin');
-- 密码: 123456
-- 密码: admin123