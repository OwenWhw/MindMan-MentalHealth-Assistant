-- Additive and re-runnable migration for existing MindMan databases.
-- Existing chat rows keep their contents; old messages default to complete.
SET @mindman_ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND column_name = 'summary') = 0,
  'ALTER TABLE chat_session ADD COLUMN summary MEDIUMTEXT NULL COMMENT ''最近一次AI会话总结，新消息到达时失效''',
  'SELECT 1'
);
PREPARE mindman_stmt FROM @mindman_ddl;
EXECUTE mindman_stmt;
DEALLOCATE PREPARE mindman_stmt;

SET @mindman_ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND column_name = 'summary_updated_at') = 0,
  'ALTER TABLE chat_session ADD COLUMN summary_updated_at DATETIME NULL COMMENT ''总结生成时间''',
  'SELECT 1'
);
PREPARE mindman_stmt FROM @mindman_ddl;
EXECUTE mindman_stmt;
DEALLOCATE PREPARE mindman_stmt;

SET @mindman_ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'delivery_status') = 0,
  'ALTER TABLE chat_message ADD COLUMN delivery_status VARCHAR(16) NOT NULL DEFAULT ''complete'' COMMENT ''streaming/complete/interrupted/failed''',
  'SELECT 1'
);
PREPARE mindman_stmt FROM @mindman_ddl;
EXECUTE mindman_stmt;
DEALLOCATE PREPARE mindman_stmt;
