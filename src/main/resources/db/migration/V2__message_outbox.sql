CREATE TABLE IF NOT EXISTS message_outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(64)  NOT NULL COMMENT '聚合类型，如 INTERVIEW_BOOKING',
    aggregate_id    VARCHAR(128) NOT NULL COMMENT '业务幂等键，如预约请求号、飞书任务号',
    event_type      VARCHAR(64)  NOT NULL COMMENT '事件类型',
    payload         JSON         NOT NULL COMMENT '消息体 JSON',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=PENDING 1=SENT 2=FAILED',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(512) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event (aggregate_type, aggregate_id, event_type),
    KEY idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务发件箱';
