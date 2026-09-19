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
-- 提示词模板表
CREATE TABLE IF NOT EXISTS `prompt_template` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `scene`      VARCHAR(64)  NOT NULL COMMENT '场景键：chat_system/quote_gen/article_recommend',
    `name`       VARCHAR(128) NOT NULL COMMENT '模板名称',
    `template`   TEXT         NOT NULL COMMENT '模板内容，支持 {变量名} 占位符',
    `variables`  VARCHAR(1024) DEFAULT NULL COMMENT '变量说明JSON：[{"key":"x","desc":"说明"}]',
    `remark`     VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `enabled`    TINYINT      DEFAULT 1 COMMENT '1启用 0停用（同场景仅一条生效）',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_scene_enabled` (`scene`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板';

-- 提示词模板种子数据
INSERT INTO `prompt_template` (`scene`, `name`, `template`, `variables`, `remark`, `enabled`) SELECT
'chat_system', 'AI咨询-默认人设（内置同款）',
'你是 MindMan，一位温暖、专业的心理健康助手。你的特点：\n\n【核心原则】\n- 以共情和倾听为主，不急于给建议\n- 使用温和、鼓励的语言，避免说教\n- 关注用户的情绪状态，而非仅关注事件本身\n- 适时使用开放式问题引导用户深入表达\n\n【回复风格】\n- 语言简洁自然，像朋友聊天一样\n- 每次回复控制在 200 字以内\n- 适当使用 emoji 增加亲和力 🌱\n- 不做医学诊断，必要时建议寻求专业帮助\n\n【情绪识别】\n- 能敏锐捕捉用户文字中的情绪信号\n- 回复中体现对用户情绪的理解和接纳\n- 不评判任何情绪，所有情绪都是合理的\n\n请始终用简体中文回复。',
'[]', '与 application.yml 内置 system-prompt 一致，可在后台修改后立即生效', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene`='chat_system');

INSERT INTO `prompt_template` (`scene`, `name`, `template`, `variables`, `remark`, `enabled`) SELECT
'quote_gen', '治愈语录生成',
'请生成 {count} 条简短的治愈系心理语录，每条不超过 30 字，温暖不鸡汤，风格清新自然，直接输出语录列表，每行一条，不要编号。',
'{"count":"生成条数"}', '首页治愈语录', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene`='quote_gen');

INSERT INTO `prompt_template` (`scene`, `name`, `template`, `variables`, `remark`, `enabled`) SELECT
'article_recommend', '文章个性化推荐理由',
'用户最近的情绪状态：{moodSummary}。请从候选文章中选择最适合 TA 的 {count} 篇，并各给一句不超过 40 字的个性化推荐理由，语气温和。',
'{"moodSummary":"近期情绪摘要","count":"推荐篇数"}', '首页 AI 文章推荐', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene`='article_recommend');

-- 文章嵌入向量表（RAG 检索用）
CREATE TABLE IF NOT EXISTS `article_embedding` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `article_id` BIGINT       NOT NULL COMMENT '所属文章ID',
    `chunk_index` INT         DEFAULT 0 COMMENT '块序号',
    `content`    TEXT         NOT NULL COMMENT '文本块内容',
    `embedding`  MEDIUMTEXT   NOT NULL COMMENT '嵌入向量JSON数组',
    `model`      VARCHAR(64)  DEFAULT NULL COMMENT '嵌入模型名',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章嵌入向量';
