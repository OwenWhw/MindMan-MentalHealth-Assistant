-- Additive, re-runnable migration. Existing ratings remain unchanged and have an unknown source.
SET @mindman_ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'emotion_record' AND column_name = 'rating_source') = 0,
  'ALTER TABLE emotion_record ADD COLUMN rating_source VARCHAR(24) DEFAULT NULL COMMENT ''self_reported=用户自评，NULL=历史来源未记录''',
  'SELECT 1'
);
PREPARE mindman_stmt FROM @mindman_ddl;
EXECUTE mindman_stmt;
DEALLOCATE PREPARE mindman_stmt;
