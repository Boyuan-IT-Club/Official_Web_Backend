-- Helpers are private to the definer procedures used by the website.
DELIMITER $$
CREATE PROCEDURE delivery_begin()
BEGIN
    IF COALESCE(GET_LOCK('official.mail.dispatch',5),0)<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Notification decision is busy';
    END IF;
    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
    START TRANSACTION;
END$$

CREATE PROCEDURE delivery_authority(IN typ VARCHAR(32), IN sid INT, IN rid INT, IN input_resume INT,
    OUT actual_resume INT, OUT revision INT, OUT actual_type VARCHAR(32), OUT eligible BOOLEAN)
BEGIN
    DECLARE state_value INT;
    DECLARE mode_value INT;
    DECLARE initial_status INT;
    SET actual_resume=NULL,revision=NULL,actual_type=typ,eligible=FALSE;
    IF typ IN ('BOOKING_SUCCESS','EVE_REMINDER','DAY_REMINDER') AND sid IS NOT NULL THEN
        SELECT s.resume_id,s.notice_revision,s.status,s.interview_mode,r.status
          INTO actual_resume,revision,state_value,mode_value,initial_status
          FROM interview_schedule s JOIN resume r ON r.resume_id=s.resume_id AND r.cycle_id=s.cycle_id
          WHERE s.schedule_id=sid AND (s.user_id IS NULL OR s.user_id=r.user_id) FOR UPDATE;
        SET eligible=COALESCE(state_value=1 AND mode_value=0 AND initial_status<>5,FALSE);
    ELSEIF rid IS NOT NULL AND (typ IS NULL OR typ IN ('ADMISSION','REJECTION')) THEN
        SELECT r.resume_id,i.decision INTO actual_resume,state_value FROM interview_result i
          JOIN resume r ON r.resume_id=i.resume_id AND r.cycle_id=i.cycle_id AND r.user_id=i.user_id
          WHERE i.result_id=rid FOR UPDATE;
        SET actual_type=CASE state_value WHEN 1 THEN 'ADMISSION' WHEN 2 THEN 'REJECTION' END;
        SET eligible=actual_type IS NOT NULL AND (typ IS NULL OR typ=actual_type);
    ELSEIF typ='RESUME_REJECTED' AND input_resume IS NOT NULL THEN
        SELECT resume_id,status INTO actual_resume,initial_status FROM resume WHERE resume_id=input_resume FOR UPDATE;
        SET eligible=COALESCE(initial_status=5,FALSE);
    END IF;
END$$

CREATE PROCEDURE delivery_hold(IN typ VARCHAR(32), IN sid INT, IN rev INT, IN rid INT, IN res INT,
    IN req VARCHAR(64), OUT held BIGINT)
BEGIN
    SET held=NULL;
    SELECT m.delivery_id INTO held FROM message_delivery m WHERE m.delivery_kind='REVIEW' AND (
      (m.review_resolution='CONFIRMED_SENT' AND req IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(m.review_evidence,'$.confirmedRequestId'))=req)
      OR (m.review_resolution='CONFIRMED_SENT' AND m.review_scope='SCHEDULE' AND m.schedule_id=sid AND m.notification_type=typ AND m.review_through_revision=rev)
      OR (m.review_resolution='OPEN' AND (m.review_scope='GLOBAL' OR (m.review_scope='TYPE' AND m.notification_type=typ)
        OR (m.review_scope='RESULT' AND m.result_id=rid)
        OR (m.review_scope='RESUME' AND m.resume_id=res AND (m.notification_type IS NULL OR m.notification_type=typ))
        OR (m.review_scope='SCHEDULE' AND m.schedule_id=sid AND m.notification_type=typ AND (m.review_through_revision IS NULL OR rev<=m.review_through_revision)))))
      ORDER BY m.delivery_id LIMIT 1 FOR UPDATE;
END$$

CREATE PROCEDURE delivery_enqueue(IN typ VARCHAR(32), IN sid INT, IN rid INT, IN input_resume INT,
    IN req VARCHAR(64), IN note TEXT)
entry: BEGIN
    DECLARE res,rev INT;
    DECLARE actual VARCHAR(32);
    DECLARE eligible BOOLEAN;
    DECLARE held,existing BIGINT;
    DECLARE outcome VARCHAR(16) DEFAULT 'SKIPPED';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; DO RELEASE_LOCK('official.mail.dispatch'); RESIGNAL; END;
    CALL delivery_begin();
    IF (SELECT enabled FROM delivery_cutover_gate)=0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Notification cutover is paused';
    END IF;
    IF req IS NULL OR LENGTH(req)=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A stable request ID is required'; END IF;
    CALL delivery_authority(typ,sid,rid,input_resume,res,rev,actual,eligible);
    IF eligible THEN
        SELECT m.delivery_id,m.mail_state INTO existing,outcome FROM message_delivery m
        WHERE ((sid IS NOT NULL AND m.delivery_kind='AUTO' AND m.schedule_id=sid AND m.schedule_revision=rev AND m.notification_type=actual)
           OR (sid IS NULL AND m.request_id=req AND m.notification_type=actual AND m.result_id<=>rid AND (rid IS NOT NULL OR m.resume_id=res)))
        ORDER BY m.delivery_id LIMIT 1 FOR UPDATE;
        CALL delivery_hold(actual,sid,rev,rid,res,req,held);
        IF existing IS NULL AND held IS NULL THEN
            INSERT INTO message_delivery(delivery_kind,event_type,request_id,notification_type,schedule_id,schedule_revision,result_id,resume_id,payload,queue_state,mail_state)
            VALUES(IF(sid IS NULL,'REQUEST','AUTO'),'EMAIL_NOTIFICATION',req,actual,sid,rev,rid,res,JSON_OBJECT('customBody',note),'PENDING','PENDING');
            SET existing=LAST_INSERT_ID(),outcome='PENDING';
        ELSEIF held IS NOT NULL AND NOT(outcome<=>'SENT') THEN
            SET outcome='UNKNOWN';
        END IF;
    END IF;
    COMMIT;
    DO RELEASE_LOCK('official.mail.dispatch');
    SELECT existing AS delivery_id,outcome AS mail_state;
END$$

CREATE PROCEDURE delivery_claim_mail(IN did BIGINT,IN req VARCHAR(64),IN envelope_type VARCHAR(32),IN envelope_rev INT,
    IN token CHAR(36),IN recipient VARCHAR(255),IN informed_time DATETIME)
BEGIN
    DECLARE sid,rid,requested_res,stored_rev,rev,res INT;
    DECLARE typ,actual,phase VARCHAR(32);
    DECLARE stored_req VARCHAR(64);
    DECLARE held BIGINT;
    DECLARE eligible BOOLEAN;
    DECLARE claimed BOOLEAN DEFAULT FALSE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; DO RELEASE_LOCK('official.mail.dispatch'); RESIGNAL; END;
    CALL delivery_begin();
    SELECT schedule_id,result_id,resume_id,schedule_revision,notification_type,request_id,mail_state
       INTO sid,rid,requested_res,stored_rev,typ,stored_req,phase FROM message_delivery
       WHERE delivery_id=did AND delivery_kind IN ('AUTO','REQUEST') AND queue_state<>'HISTORICAL' FOR UPDATE;
    IF (SELECT enabled FROM delivery_cutover_gate)=1 AND phase='PENDING' AND req=stored_req AND envelope_type=typ AND envelope_rev<=>stored_rev THEN
        CALL delivery_authority(typ,sid,rid,requested_res,res,rev,actual,eligible);
        CALL delivery_hold(typ,sid,stored_rev,rid,requested_res,req,held);
        IF NOT eligible OR NOT(rev<=>stored_rev) OR NOT(res<=>requested_res) THEN
            UPDATE message_delivery SET mail_state='SKIPPED',mail_error='Business record changed' WHERE delivery_id=did;
        ELSEIF held IS NOT NULL THEN
            UPDATE message_delivery SET mail_error=CONCAT('review:',held) WHERE delivery_id=did;
        ELSE
            IF recipient IS NULL OR LENGTH(recipient)=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing prepared recipient'; END IF;
            UPDATE message_delivery SET mail_state='SENDING',mail_attempts=mail_attempts+1,mail_lease_token=token,
              mail_lease_until=NOW(6)+INTERVAL 10 MINUTE,recipient_email=recipient,notified_interview_time=informed_time,
              queue_state='PUBLISHED',published_at=COALESCE(published_at,NOW()),queue_lease_token=NULL,queue_lease_until=NULL
              WHERE delivery_id=did;
            SET claimed=TRUE;
        END IF;
    ELSEIF (SELECT enabled FROM delivery_cutover_gate)=0 AND phase='PENDING' AND req=stored_req
           AND envelope_type=typ AND envelope_rev<=>stored_rev THEN
        UPDATE message_delivery SET queue_state='PENDING',queue_lease_token=NULL,queue_lease_until=NULL
        WHERE delivery_id=did AND queue_state='PUBLISHED';
    END IF;
    COMMIT;
    DO RELEASE_LOCK('official.mail.dispatch');
    SELECT claimed AS claimed;
END$$

CREATE PROCEDURE delivery_finish_mail(IN did BIGINT,IN token CHAR(36),IN accepted BOOLEAN,IN detail VARCHAR(512))
BEGIN
    DECLARE sid,rid,rev INT;
    DECLARE typ VARCHAR(32);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; DO RELEASE_LOCK('official.mail.dispatch'); RESIGNAL; END;
    CALL delivery_begin();
    SELECT schedule_id,result_id,schedule_revision,notification_type INTO sid,rid,rev,typ FROM message_delivery
       WHERE delivery_id=did AND mail_state='SENDING' AND mail_lease_token=token FOR UPDATE;
    UPDATE message_delivery SET mail_state=IF(accepted,'SENT','UNKNOWN'),mail_sent_at=IF(accepted,NOW(),NULL),
       mail_error=detail,mail_lease_until=NULL,mail_lease_token=NULL WHERE delivery_id=did AND mail_state='SENDING' AND mail_lease_token=token;
    IF ROW_COUNT()=1 AND accepted THEN
       IF sid IS NOT NULL AND typ='BOOKING_SUCCESS' THEN
          UPDATE interview_schedule SET notif_status=1 WHERE schedule_id=sid AND notice_revision=rev;
       ELSEIF rid IS NOT NULL THEN
          UPDATE interview_result SET notified_at=NOW() WHERE result_id=rid AND decision=IF(typ='ADMISSION',1,2);
       END IF;
    END IF;
    COMMIT;
    DO RELEASE_LOCK('official.mail.dispatch');
END$$
DELIMITER ;

DELIMITER $$
CREATE PROCEDURE delivery_queue_task(IN aggregate_name VARCHAR(64), IN aggregate_key VARCHAR(128), IN event_name VARCHAR(64), IN body JSON)
BEGIN
    IF NOT ((event_name='FEISHU_SYNC' AND aggregate_name='FEISHU_SYNC') OR (event_name='INTERVIEW_BOOKING_PERSIST' AND aggregate_name='INTERVIEW_BOOKING')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown transactional task';
    END IF;
    INSERT INTO message_delivery(delivery_kind,event_type,aggregate_type,aggregate_id,payload,queue_state,mail_state)
      VALUES('TASK',event_name,aggregate_name,aggregate_key,body,'PENDING','NA')
      ON DUPLICATE KEY UPDATE delivery_id=delivery_id;
END$$

CREATE PROCEDURE delivery_lease_queue(IN token CHAR(36), IN max_tries INT)
BEGIN
    DECLARE did BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
    START TRANSACTION;
    IF (SELECT enabled FROM delivery_cutover_gate)=1 THEN
        UPDATE message_delivery SET mail_state='UNKNOWN',mail_error='SMTP acceptance unknown after lease expired',
             mail_lease_token=NULL,mail_lease_until=NULL WHERE mail_state='SENDING' AND mail_lease_until<=NOW(6);
        SELECT delivery_id INTO did FROM message_delivery WHERE
          (queue_state='PENDING' OR (queue_state='PUBLISHING' AND queue_lease_until<=NOW(6)))
          AND delivery_kind IN ('AUTO','REQUEST','TASK') AND mail_state IN ('PENDING','NA')
          ORDER BY delivery_id LIMIT 1 FOR UPDATE SKIP LOCKED;
        IF did IS NOT NULL THEN
          UPDATE message_delivery SET queue_state=IF(queue_attempts>=max_tries,'FAILED','PUBLISHING'),
              queue_lease_token=IF(queue_attempts>=max_tries,NULL,token),queue_lease_until=NOW(6)+INTERVAL 90 SECOND,
              queue_attempts=queue_attempts+1 WHERE delivery_id=did;
        END IF;
    END IF;
    COMMIT;
    SELECT delivery_id,delivery_kind,event_type,request_id,notification_type,schedule_revision,schedule_id,result_id,resume_id,payload
      FROM message_delivery WHERE delivery_id=did AND queue_state='PUBLISHING' AND queue_lease_token=token;
END$$

CREATE PROCEDURE delivery_ack_queue(IN did BIGINT,IN token CHAR(36),IN ok BOOLEAN,IN detail VARCHAR(512),IN max_tries INT)
BEGIN
    UPDATE message_delivery SET queue_state=CASE WHEN ok THEN 'PUBLISHED' WHEN queue_attempts>=max_tries THEN 'FAILED' ELSE 'PENDING' END,
      queue_error=IF(ok,NULL,detail),published_at=IF(ok,COALESCE(published_at,NOW()),published_at),
      queue_lease_token=NULL,queue_lease_until=NULL WHERE delivery_id=did AND queue_state='PUBLISHING' AND queue_lease_token=token;
END$$

CREATE PROCEDURE delivery_observe_mail(IN did BIGINT,IN req VARCHAR(64),IN typ VARCHAR(32),IN rev INT)
BEGIN
    IF (SELECT enabled FROM delivery_cutover_gate)=0 THEN
        UPDATE message_delivery SET queue_state='PENDING',queue_lease_token=NULL,queue_lease_until=NULL
        WHERE delivery_id=did AND delivery_kind IN ('AUTO','REQUEST') AND mail_state='PENDING'
          AND request_id=req AND notification_type=typ AND schedule_revision<=>rev;
    ELSE
        UPDATE message_delivery SET queue_state='PUBLISHED',published_at=COALESCE(published_at,NOW()),
          queue_lease_token=NULL,queue_lease_until=NULL WHERE delivery_id=did AND delivery_kind IN ('AUTO','REQUEST')
          AND request_id=req AND notification_type=typ AND schedule_revision<=>rev AND queue_state<>'HISTORICAL';
    END IF;
    SELECT delivery_id,request_id,notification_type,schedule_id,schedule_revision,result_id,resume_id,payload
       FROM message_delivery WHERE delivery_id=did AND delivery_kind IN ('AUTO','REQUEST') AND request_id=req
       AND notification_type=typ AND schedule_revision<=>rev AND mail_state='PENDING' AND queue_state='PUBLISHED';
END$$

CREATE PROCEDURE delivery_reject_preflight(IN did BIGINT,IN req VARCHAR(64),IN phase VARCHAR(16),IN detail VARCHAR(512))
BEGIN
    IF phase NOT IN ('FAILED','SKIPPED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid preflight result'; END IF;
    UPDATE message_delivery SET mail_state=phase,mail_error=detail WHERE delivery_id=did AND request_id=req
      AND delivery_kind IN ('AUTO','REQUEST') AND queue_state='PUBLISHED' AND mail_state='PENDING';
END$$

CREATE PROCEDURE delivery_quarantine(IN source_key VARCHAR(160),IN body JSON,IN typ VARCHAR(32),IN sid INT,IN rid INT,IN res INT)
BEGIN
    DECLARE target_schedule,target_result,target_resume INT;
    DECLARE scope_name VARCHAR(16) DEFAULT 'UNMATCHED';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; DO RELEASE_LOCK('official.mail.dispatch'); RESIGNAL; END;
    CALL delivery_begin();
    IF source_key IS NULL OR body IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing original message evidence'; END IF;
    IF typ IN ('BOOKING_SUCCESS','EVE_REMINDER','DAY_REMINDER') AND sid IS NOT NULL THEN
        SELECT schedule_id INTO target_schedule FROM interview_schedule WHERE schedule_id=sid FOR UPDATE;
    END IF;
    IF rid IS NOT NULL THEN SELECT result_id INTO target_result FROM interview_result WHERE result_id=rid FOR UPDATE; END IF;
    IF res IS NOT NULL THEN SELECT resume_id INTO target_resume FROM resume WHERE resume_id=res FOR UPDATE; END IF;
    SET scope_name=CASE WHEN target_schedule IS NOT NULL THEN 'SCHEDULE' WHEN target_result IS NOT NULL THEN 'RESULT'
                        WHEN target_resume IS NOT NULL THEN 'RESUME' ELSE 'UNMATCHED' END;
    INSERT INTO message_delivery(delivery_kind,event_type,notification_type,review_scope,review_key,review_resolution,
       review_evidence,schedule_id,result_id,resume_id,queue_state,mail_state)
       VALUES('REVIEW','EMAIL_NOTIFICATION',typ,scope_name,source_key,'OPEN',JSON_OBJECT('brokerMessage',body),
              target_schedule,target_result,target_resume,'HISTORICAL','UNKNOWN') ON DUPLICATE KEY UPDATE delivery_id=delivery_id;
    COMMIT;
    DO RELEASE_LOCK('official.mail.dispatch');
END$$
DELIMITER ;

DELIMITER $$
CREATE PROCEDURE delivery_resolve(IN review_id BIGINT,IN resolution VARCHAR(24),IN operator_id INT,IN evidence TEXT,
    IN exact_schedule INT,IN exact_revision INT,IN exact_type VARCHAR(32),IN exact_request VARCHAR(64))
BEGIN
    DECLARE scope_name,old_type VARCHAR(32);
    DECLARE old_schedule,old_result,old_resume,upto INT;
    DECLARE req_result,req_resume INT;
    DECLARE req_kind VARCHAR(32);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; DO RELEASE_LOCK('official.mail.dispatch'); RESIGNAL; END;
    CALL delivery_begin();
    SELECT review_scope,schedule_id,result_id,resume_id,notification_type,review_through_revision
      INTO scope_name,old_schedule,old_result,old_resume,old_type,upto FROM message_delivery
      WHERE delivery_id=review_id AND delivery_kind='REVIEW' AND review_resolution='OPEN' FOR UPDATE;
    IF scope_name IS NULL OR resolution NOT IN ('CONFIRMED_SENT','CONFIRMED_UNSENT') OR operator_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM `user` WHERE user_id=operator_id) OR COALESCE(LENGTH(TRIM(evidence)),0)=0
       THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Open review, operator and evidence required'; END IF;
    IF resolution='CONFIRMED_SENT' THEN
      IF exact_request IS NOT NULL THEN
        SELECT result_id,resume_id,notification_type INTO req_result,req_resume,req_kind FROM message_delivery
          WHERE request_id=exact_request AND notification_type IN ('ADMISSION','REJECTION','RESUME_REJECTED');
        IF req_kind IS NULL AND scope_name='RESULT' AND exact_type IN ('ADMISSION','REJECTION') AND
             CHAR_LENGTH(TRIM(exact_request))>0 AND NOT EXISTS(SELECT 1 FROM message_delivery WHERE request_id=exact_request) THEN
          -- An old queued action can have a provider receipt but no surviving delivery row.
          SET req_kind=exact_type,req_result=old_result,req_resume=old_resume;
        ELSEIF req_kind IS NULL AND scope_name='RESUME' AND exact_type='RESUME_REJECTED' AND
             CHAR_LENGTH(TRIM(exact_request))>0 AND NOT EXISTS(SELECT 1 FROM message_delivery WHERE request_id=exact_request) THEN
          SET req_kind=exact_type,req_resume=old_resume;
        END IF;
        IF req_kind IS NULL OR (old_result IS NOT NULL AND NOT(req_result<=>old_result)) OR
           (old_resume IS NOT NULL AND NOT(req_resume<=>old_resume)) OR (old_type IS NOT NULL AND old_type<>req_kind)
           OR (exact_type IS NOT NULL AND exact_type<>req_kind) OR scope_name='SCHEDULE'
           THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Evidence must identify a matching mail request'; END IF;
        UPDATE message_delivery SET review_scope=IF(req_result IS NULL,'RESUME','RESULT'),
          result_id=req_result,resume_id=req_resume,notification_type=req_kind WHERE delivery_id=review_id;
      ELSE
        IF exact_schedule IS NULL OR exact_revision IS NULL OR exact_revision<1 OR exact_type IS NULL OR
           exact_type NOT IN ('BOOKING_SUCCESS','EVE_REMINDER','DAY_REMINDER') OR
           (old_schedule IS NOT NULL AND old_schedule<>exact_schedule) OR (old_type IS NOT NULL AND old_type<>exact_type)
           OR (upto IS NOT NULL AND exact_revision>upto) OR old_result IS NOT NULL OR NOT EXISTS (
             SELECT 1 FROM interview_schedule s WHERE s.schedule_id=exact_schedule AND s.notice_revision>=exact_revision
                  AND (old_resume IS NULL OR s.resume_id=old_resume))
          THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Evidence must identify an exact schedule version'; END IF;
        UPDATE message_delivery SET review_scope='SCHEDULE',schedule_id=exact_schedule,
          review_through_revision=exact_revision,notification_type=exact_type WHERE delivery_id=review_id;
      END IF;
    END IF;
    UPDATE message_delivery SET review_resolution=resolution,
      review_evidence=JSON_MERGE_PATCH(COALESCE(review_evidence,JSON_OBJECT()),JSON_OBJECT('evidence',evidence,
        'operatorId',operator_id,'resolvedAt',NOW(6),'confirmedRequestId',IF(resolution='CONFIRMED_SENT',exact_request,NULL)))
      WHERE delivery_id=review_id;
    UPDATE message_delivery SET queue_state='PENDING',queue_attempts=0,mail_error=NULL WHERE mail_state='PENDING'
      AND queue_state='PUBLISHED' AND mail_error=CONCAT('review:',review_id);
    COMMIT;
    DO RELEASE_LOCK('official.mail.dispatch');
END$$
DELIMITER ;

-- An SMTP outcome can only be settled with provider evidence. Unsent is terminal:
-- a separate eligible action must request any subsequent send.
DELIMITER $$
CREATE PROCEDURE delivery_resolve_unknown(IN did BIGINT,IN operator_id INT,IN evidence TEXT,IN accepted BOOLEAN,IN accepted_at DATETIME)
BEGIN
    DECLARE sid,rid,rev INT;
    DECLARE typ VARCHAR(32);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; DO RELEASE_LOCK('official.mail.dispatch'); RESIGNAL; END;
    CALL delivery_begin();
    IF accepted IS NULL OR (accepted AND (accepted_at IS NULL OR accepted_at>NOW())) OR
       (NOT accepted AND accepted_at IS NOT NULL) OR COALESCE(CHAR_LENGTH(TRIM(evidence)),0)=0 OR
       NOT EXISTS(SELECT 1 FROM `user` WHERE user_id=operator_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider receipt and operator required';
    END IF;
    SELECT schedule_id,result_id,schedule_revision,notification_type INTO sid,rid,rev,typ FROM message_delivery
      WHERE delivery_id=did AND delivery_kind IN ('AUTO','REQUEST') AND mail_state='UNKNOWN' FOR UPDATE;
    IF typ IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No ambiguous SMTP attempt'; END IF;
    UPDATE message_delivery SET mail_state=IF(accepted,'SENT','FAILED'),mail_sent_at=accepted_at,
      review_evidence=JSON_OBJECT('providerEvidence',evidence,'operatorId',operator_id,'resolvedAt',NOW(6),'accepted',accepted),
      mail_error=IF(accepted,NULL,'Provider confirmed no delivery') WHERE delivery_id=did;
    IF accepted AND sid IS NOT NULL AND typ='BOOKING_SUCCESS' THEN
      UPDATE interview_schedule SET notif_status=1 WHERE schedule_id=sid AND notice_revision=rev;
    ELSEIF accepted AND rid IS NOT NULL THEN
      UPDATE interview_result SET notified_at=GREATEST(COALESCE(notified_at,accepted_at),accepted_at)
        WHERE result_id=rid AND decision=IF(typ='ADMISSION',1,2);
    END IF;
    COMMIT;
    DO RELEASE_LOCK('official.mail.dispatch');
END$$
DELIMITER ;
