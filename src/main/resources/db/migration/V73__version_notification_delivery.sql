-- Both application nodes first roll to a version that understands delivery IDs.
-- Opening the relay is a separate operation after the old broker consumers drain.
CREATE SQL SECURITY INVOKER VIEW delivery_cutover_gate AS SELECT 0 AS enabled;

CREATE SQL SECURITY INVOKER VIEW delivery_sent_log AS
SELECT delivery_id AS id,notification_type,schedule_id,result_id,resume_id,request_id,recipient_email,
       notified_interview_time,mail_sent_at AS sent_at
FROM message_delivery WHERE mail_state='SENT' AND (SELECT enabled FROM delivery_cutover_gate)
UNION ALL
SELECT id,notification_type,schedule_id,result_id,resume_id,request_id,recipient_email,notified_interview_time,sent_at
FROM interview_notification_log WHERE NOT (SELECT enabled FROM delivery_cutover_gate);

DELIMITER $$
CREATE TRIGGER interview_schedule_notice_version BEFORE UPDATE ON interview_schedule FOR EACH ROW
BEGIN
  IF NOT((OLD.interview_time<=>NEW.interview_time) AND (OLD.session_id<=>NEW.session_id)
      AND (OLD.status<=>NEW.status) AND (OLD.interview_mode<=>NEW.interview_mode) AND (OLD.dept_id<=>NEW.dept_id)
      AND (OLD.resume_id<=>NEW.resume_id) AND (OLD.cycle_id<=>NEW.cycle_id) AND (OLD.user_id<=>NEW.user_id))
      OR NEW.notice_revision>OLD.notice_revision THEN
    SET NEW.notice_revision=OLD.notice_revision+1,NEW.notif_status=0;
  ELSE
    SET NEW.notice_revision=OLD.notice_revision;
  END IF;
END$$
CREATE TRIGGER interview_session_notice_location AFTER UPDATE ON interview_session FOR EACH ROW
BEGIN
  IF NOT (OLD.location<=>NEW.location) THEN
    UPDATE interview_schedule SET notice_revision=notice_revision+1
    WHERE session_id=NEW.session_id AND status=1 AND interview_mode=0;
  END IF;
END$$

CREATE PROCEDURE import_legacy_delivery()
BEGIN
  DECLARE original_zone VARCHAR(64) DEFAULT @@session.time_zone;
  DECLARE locked INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET SESSION time_zone=original_zone;
    IF locked=1 THEN DO RELEASE_LOCK('official.mail.dispatch'); END IF;
    RESIGNAL;
  END;
  SET SESSION time_zone='+08:00';
  SELECT GET_LOCK('official.mail.dispatch',10) INTO locked;
  IF COALESCE(locked,0)<>1 OR (SELECT enabled FROM delivery_cutover_gate)<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import requires a frozen delivery queue';
  END IF;
  SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
  START TRANSACTION;
    INSERT INTO message_delivery (delivery_kind,event_type,aggregate_type,aggregate_id,payload,
      queue_state,queue_attempts,queue_error,published_at,mail_state,created_at,legacy_outbox_id)
    SELECT 'TASK',o.event_type,o.aggregate_type,o.aggregate_id,o.payload,
      CASE o.status WHEN 0 THEN 'PENDING' WHEN 1 THEN 'PUBLISHED' WHEN 2 THEN 'FAILED' END,
      o.retry_count,o.last_error,o.sent_at,'NA',o.created_at,o.id FROM message_outbox o
    WHERE NOT EXISTS (SELECT 1 FROM message_delivery m WHERE m.legacy_outbox_id=o.id);
    -- A legacy task can finish between initial inventory and the final frozen import.
    UPDATE message_delivery m JOIN message_outbox o ON o.id=m.legacy_outbox_id
      SET m.queue_state=CASE o.status WHEN 0 THEN 'PENDING' WHEN 1 THEN 'PUBLISHED' ELSE 'FAILED' END,
          m.queue_attempts=o.retry_count,m.queue_error=o.last_error,m.published_at=o.sent_at
      WHERE m.delivery_kind='TASK';
    INSERT INTO message_delivery (delivery_kind,event_type,request_id,notification_type,schedule_id,schedule_revision,
      result_id,resume_id,recipient_email,notified_interview_time,queue_state,mail_state,mail_sent_at,created_at,legacy_log_id)
    SELECT 'HISTORY','EMAIL_NOTIFICATION',l.request_id,l.notification_type,l.schedule_id,NULL,
      l.result_id,l.resume_id,l.recipient_email,l.notified_interview_time,'HISTORICAL','SENT',l.sent_at,l.sent_at,l.id
    FROM interview_notification_log l
    WHERE NOT EXISTS (SELECT 1 FROM message_delivery m WHERE m.legacy_log_id=l.id);

    INSERT INTO message_delivery (delivery_kind,event_type,notification_type,schedule_id,resume_id,
      review_key,review_scope,review_through_revision,review_resolution,review_evidence,queue_state,mail_state)
    SELECT 'REVIEW','EMAIL_NOTIFICATION',l.notification_type,s.schedule_id,l.resume_id,CONCAT('log:',l.id),
      CASE WHEN s.schedule_id IS NOT NULL THEN 'SCHEDULE' WHEN l.resume_id IS NOT NULL THEN 'RESUME' ELSE 'UNMATCHED' END,
      s.notice_revision,'OPEN',JSON_OBJECT('legacyLogId',l.id),'HISTORICAL','UNKNOWN'
    FROM interview_notification_log l
    LEFT JOIN interview_schedule s ON s.schedule_id=l.schedule_id
    WHERE l.notification_type IN ('BOOKING_SUCCESS','EVE_REMINDER','DAY_REMINDER')
      AND NOT EXISTS (SELECT 1 FROM message_delivery m WHERE m.review_key=CONCAT('log:',l.id));
    INSERT INTO message_delivery (delivery_kind,event_type,schedule_id,notification_type,
      review_key,review_scope,review_through_revision,review_resolution,review_evidence,queue_state,mail_state)
    SELECT 'REVIEW','EMAIL_NOTIFICATION',s.schedule_id,'BOOKING_SUCCESS',CONCAT('schedule:',s.schedule_id,':1:BOOKING_SUCCESS'),
      'SCHEDULE',1,'OPEN',JSON_OBJECT('legacyNotifStatus',s.notif_status),'HISTORICAL','UNKNOWN'
    FROM interview_schedule s WHERE s.notif_status=1
      AND NOT EXISTS (SELECT 1 FROM interview_notification_log l WHERE l.schedule_id=s.schedule_id AND l.notification_type='BOOKING_SUCCESS')
      AND NOT EXISTS (SELECT 1 FROM message_delivery m WHERE m.review_key=CONCAT('schedule:',s.schedule_id,':1:BOOKING_SUCCESS'));
    INSERT INTO message_delivery (delivery_kind,event_type,result_id,review_key,review_scope,
      review_resolution,review_evidence,queue_state,mail_state)
    SELECT 'REVIEW','EMAIL_NOTIFICATION',r.result_id,CONCAT('result:',r.result_id),'RESULT','OPEN',
      JSON_OBJECT('legacyNotifiedAt',r.notified_at),'HISTORICAL','UNKNOWN' FROM interview_result r
    WHERE r.notified_at IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM interview_notification_log l WHERE l.result_id=r.result_id AND l.sent_at>=r.notified_at
        AND ((r.decision=1 AND l.notification_type='ADMISSION') OR (r.decision=2 AND l.notification_type='REJECTION')))
      AND NOT EXISTS (SELECT 1 FROM message_delivery m WHERE m.review_key=CONCAT('result:',r.result_id));

  IF EXISTS(SELECT 1 FROM message_outbox o LEFT JOIN message_delivery m ON m.legacy_outbox_id=o.id
    WHERE m.delivery_id IS NULL OR NOT(m.payload <=> CAST(o.payload AS JSON)) OR m.aggregate_id<>o.aggregate_id
    OR m.delivery_kind<>'TASK' OR m.aggregate_type<>o.aggregate_type OR m.queue_attempts<>o.retry_count
    OR NOT(m.queue_error<=>o.last_error) OR NOT(m.published_at<=>o.sent_at) OR NOT(m.created_at<=>o.created_at)
    OR m.event_type<>o.event_type OR m.queue_state<>CASE o.status WHEN 0 THEN 'PENDING' WHEN 1 THEN 'PUBLISHED' ELSE 'FAILED' END)
  OR EXISTS(SELECT 1 FROM interview_notification_log l LEFT JOIN message_delivery m ON m.legacy_log_id=l.id
    WHERE m.delivery_id IS NULL OR m.queue_state<>'HISTORICAL' OR m.mail_state<>'SENT' OR m.notification_type<>l.notification_type
    OR NOT(m.created_at<=>l.sent_at) OR NOT(m.request_id<=>l.request_id) OR NOT(m.mail_sent_at<=>l.sent_at)
    OR NOT(m.schedule_id<=>l.schedule_id) OR NOT(m.result_id<=>l.result_id) OR NOT(m.resume_id<=>l.resume_id)
    OR NOT(m.recipient_email<=>l.recipient_email) OR NOT(m.notified_interview_time<=>l.notified_interview_time))
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy delivery reconciliation failed'; END IF;
  COMMIT;
  SET SESSION time_zone=original_zone;
  DO RELEASE_LOCK('official.mail.dispatch');
END$$
DELIMITER ;
