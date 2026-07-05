-- 幂等：表不存在时跳过（空库由 JPA 建表，实体已含 feishu_record_id）；列/索引已存在时跳过
SET @db := DATABASE();

SET @tbl_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'interview_schedule'
);

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'interview_schedule'
      AND COLUMN_NAME = 'feishu_record_id'
);
SET @ddl := IF(@tbl_exists > 0 AND @col_exists = 0,
    'ALTER TABLE interview_schedule ADD COLUMN feishu_record_id VARCHAR(64) NULL COMMENT ''飞书多维表格行 record_id'' AFTER sync_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'interview_schedule'
      AND INDEX_NAME = 'idx_schedule_feishu_record'
);
SET @idx := IF(@tbl_exists > 0 AND @idx_exists = 0,
    'CREATE INDEX idx_schedule_feishu_record ON interview_schedule (feishu_record_id)',
    'SELECT 1');
PREPARE stmt2 FROM @idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
