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
