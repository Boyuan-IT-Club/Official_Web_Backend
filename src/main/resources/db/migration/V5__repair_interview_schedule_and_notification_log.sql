-- 修复：V3 若在 interview_schedule 建表之前已执行会被 Flyway 记为成功但并未加列；本脚本幂等补全。

SET @db := DATABASE();

-- interview_schedule.feishu_record_id
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

-- interview_notification_log（V4 未执行或库较旧时补建）
CREATE TABLE IF NOT EXISTS interview_notification_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    notification_type VARCHAR(32)  NOT NULL COMMENT '通知类型',
    schedule_id       INT          NULL COMMENT '面试安排ID',
    result_id         INT          NULL COMMENT '面试结果ID',
    recipient_email   VARCHAR(255) NOT NULL COMMENT '收件人邮箱',
    sent_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_schedule (notification_type, schedule_id),
    UNIQUE KEY uk_type_result (notification_type, result_id),
    KEY idx_schedule_id (schedule_id),
    KEY idx_result_id (result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试通知发送记录';
