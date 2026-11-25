CREATE TABLE message_delivery (
  delivery_id BIGINT NOT NULL AUTO_INCREMENT,
  delivery_kind ENUM('TASK','AUTO','REQUEST','HISTORY','REVIEW') NOT NULL,
  event_type VARCHAR(64) NOT NULL COMMENT 'EMAIL_NOTIFICATION / FEISHU_SYNC / 历史预约事件',
  aggregate_type VARCHAR(64) NULL,
  aggregate_id VARCHAR(128) NULL,
  payload JSON NULL COMMENT '历史发送日志无需补造消息体',
  request_id VARCHAR(64) NULL COMMENT '邮件请求标识；无请求号的旧日志可空',
  notification_type VARCHAR(32) NULL,
  schedule_id INT NULL,
  schedule_revision INT NULL COMMENT '仅对安排、前一天、当天三类自动通知赋值',
  result_id INT NULL,
  resume_id INT NULL,
  recipient_email VARCHAR(255) NULL,
  notified_interview_time DATETIME NULL COMMENT '实际邮件里告知的面试时间',
  queue_state VARCHAR(16) NOT NULL COMMENT 'PENDING/PUBLISHING/PUBLISHED/FAILED/HISTORICAL',
  queue_attempts INT NOT NULL DEFAULT 0,
  queue_error VARCHAR(512) NULL,
  queue_lease_token CHAR(36) NULL,
  queue_lease_until DATETIME(6) NULL,
  published_at DATETIME NULL,
  mail_state VARCHAR(16) NOT NULL COMMENT 'NA/PENDING/SENDING/SENT/FAILED/UNKNOWN/SKIPPED',
  mail_attempts INT NOT NULL DEFAULT 0,
  mail_error VARCHAR(512) NULL,
  mail_lease_token CHAR(36) NULL,
  mail_lease_until DATETIME(6) NULL,
  mail_sent_at DATETIME NULL COMMENT 'SMTP 接受的时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  legacy_outbox_id BIGINT NULL,
  legacy_log_id BIGINT NULL,
  review_key VARCHAR(160) NULL COMMENT '来源与业务主键，如 result:18 或 schedule:28:1:BOOKING_SUCCESS',
  review_resolution VARCHAR(24) NULL COMMENT 'OPEN/CONFIRMED_SENT/CONFIRMED_UNSENT',
  review_scope ENUM('SCHEDULE','RESULT','RESUME','UNMATCHED','TYPE','GLOBAL') NULL,
  review_through_revision INT NULL COMMENT 'OPEN 为隔离上界，CONFIRMED_SENT 为精确版本；未知上界为空',
  review_evidence JSON NULL COMMENT '来源、核对凭据、经办人及决定时间',
  PRIMARY KEY (delivery_id),
  UNIQUE KEY uq_delivery_outbox_origin (legacy_outbox_id),
  UNIQUE KEY uq_delivery_log_origin (legacy_log_id),
  UNIQUE KEY uq_delivery_event (aggregate_type,aggregate_id,event_type),
  UNIQUE KEY uq_delivery_request (request_id),
  UNIQUE KEY uq_delivery_auto_notice (notification_type,schedule_id,schedule_revision),
  UNIQUE KEY uq_delivery_review (review_key),
  -- 三元业务键仅给任务。预约 aggregate_id 与邮件 request_id 即使同值也可共存。
  CONSTRAINT ck_delivery_task_keys CHECK (
    (delivery_kind='TASK' AND aggregate_type IS NOT NULL AND aggregate_id IS NOT NULL
      AND request_id IS NULL AND notification_type IS NULL AND mail_state='NA')
    OR (delivery_kind<>'TASK' AND aggregate_type IS NULL AND aggregate_id IS NULL
      AND event_type='EMAIL_NOTIFICATION' AND mail_state<>'NA')),
  -- 只有 AUTO 占用排期版本。一条已证实的当前历史成功记录也可占槽，重复调度直接读它。
  CONSTRAINT ck_delivery_auto_keys CHECK (
    (delivery_kind='AUTO' AND notification_type IS NOT NULL
      AND notification_type IN ('BOOKING_SUCCESS','EVE_REMINDER','DAY_REMINDER')
      AND schedule_id IS NOT NULL AND schedule_revision IS NOT NULL AND schedule_revision>0
      AND result_id IS NULL
      AND ((queue_state='HISTORICAL' AND legacy_log_id IS NOT NULL AND mail_state='SENT')
           OR (queue_state<>'HISTORICAL' AND request_id IS NOT NULL)))
    OR (delivery_kind<>'AUTO' AND schedule_revision IS NULL)),
  CONSTRAINT ck_delivery_manual_keys CHECK (delivery_kind<>'REQUEST' OR
    (request_id IS NOT NULL AND schedule_id IS NULL AND notification_type IS NOT NULL AND
      ((notification_type IN ('ADMISSION','REJECTION') AND result_id IS NOT NULL)
       OR (notification_type='RESUME_REJECTED' AND resume_id IS NOT NULL AND result_id IS NULL)))),
  CONSTRAINT ck_delivery_history CHECK (delivery_kind<>'HISTORY' OR
    (legacy_log_id IS NOT NULL AND queue_state='HISTORICAL' AND mail_state='SENT')),
  CONSTRAINT ck_delivery_review CHECK (
    (delivery_kind='REVIEW' AND review_key IS NOT NULL AND review_resolution IS NOT NULL
      AND review_resolution IN ('OPEN','CONFIRMED_SENT','CONFIRMED_UNSENT')
      AND queue_state='HISTORICAL' AND request_id IS NULL AND mail_state='UNKNOWN')
    OR (delivery_kind<>'REVIEW' AND review_key IS NULL AND review_resolution IS NULL)),
  CONSTRAINT ck_delivery_hold_scope CHECK (
    (delivery_kind<>'REVIEW' AND review_scope IS NULL AND review_through_revision IS NULL)
    OR (delivery_kind='REVIEW' AND review_scope IS NOT NULL AND
      (review_through_revision IS NULL OR (review_scope='SCHEDULE' AND review_through_revision>0)) AND
      ((review_scope='SCHEDULE' AND schedule_id IS NOT NULL AND notification_type IS NOT NULL)
       OR (review_scope='RESULT' AND result_id IS NOT NULL)
       OR (review_scope='RESUME' AND resume_id IS NOT NULL)
       OR (review_scope='TYPE' AND notification_type IS NOT NULL)
       OR (review_scope='UNMATCHED' AND schedule_id IS NULL AND resume_id IS NULL AND result_id IS NULL)
       OR review_scope='GLOBAL'))),
  CONSTRAINT ck_delivery_origin CHECK (
    (legacy_outbox_id IS NULL OR delivery_kind='TASK')
    AND (legacy_log_id IS NULL OR
      (delivery_kind IN ('AUTO','HISTORY') AND queue_state='HISTORICAL' AND mail_state='SENT'))),
  KEY ix_delivery_relay (queue_state,created_at,delivery_id),
  KEY ix_delivery_mail (mail_state,mail_lease_until),
  KEY ix_delivery_schedule (schedule_id,notification_type,mail_state),
  KEY ix_delivery_result (result_id,mail_state,mail_sent_at),
  KEY ix_delivery_resume (resume_id,notification_type,mail_state,mail_sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
