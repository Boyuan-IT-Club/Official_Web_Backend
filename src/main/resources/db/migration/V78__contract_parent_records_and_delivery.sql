-- Run after every deployed reader and writer has switched to the parent records and delivery IDs.
SET SESSION lock_wait_timeout=10;
SET @contract_original_zone=@@session.time_zone;
SET SESSION time_zone='+08:00';
CREATE TEMPORARY TABLE website_contract_guard (ok TINYINT NOT NULL CHECK(ok=1));
INSERT INTO website_contract_guard SELECT (SELECT enabled FROM parent_record_cutover)=1 OR
    (NOT EXISTS(SELECT 1 FROM recruitment_qr_code) AND NOT EXISTS(SELECT 1 FROM pre_admission_draft) AND
     NOT EXISTS(SELECT 1 FROM interview_preference_time) AND NOT EXISTS(SELECT 1 FROM interview_session_dept));
INSERT INTO website_contract_guard SELECT (SELECT enabled FROM delivery_cutover_gate)=1 OR
    (NOT EXISTS(SELECT 1 FROM interview_notification_log) AND NOT EXISTS(SELECT 1 FROM message_outbox));
INSERT INTO website_contract_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_schedule s LEFT JOIN legacy_interview_slot_source v ON v.slot_id=s.slot_id
    WHERE s.slot_id IS NOT NULL AND (v.slot_id IS NULL OR NOT(v.snapshot<=>s.legacy_slot_snapshot)));
INSERT INTO website_contract_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_session WHERE dept_ids IS NULL OR JSON_LENGTH(dept_ids)=0 OR
    NOT(dept_id<=>JSON_VALUE(dept_ids,'$[0]' RETURNING SIGNED)));
INSERT INTO website_contract_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_notification_log l LEFT JOIN message_delivery m ON m.legacy_log_id=l.id
    WHERE m.delivery_id IS NULL OR m.queue_state<>'HISTORICAL' OR m.mail_state<>'SENT' OR
      NOT(m.notification_type<=>l.notification_type) OR NOT(m.request_id<=>l.request_id) OR
      NOT(m.mail_sent_at<=>l.sent_at) OR NOT(m.result_id<=>l.result_id) OR NOT(m.resume_id<=>l.resume_id) OR
      NOT(m.schedule_id<=>l.schedule_id) OR NOT(m.recipient_email<=>l.recipient_email) OR
      NOT(m.notified_interview_time<=>l.notified_interview_time));
INSERT INTO website_contract_guard SELECT NOT EXISTS (
    SELECT 1 FROM message_outbox o LEFT JOIN message_delivery m ON m.legacy_outbox_id=o.id
    WHERE m.delivery_id IS NULL OR m.delivery_kind<>'TASK' OR NOT(m.payload<=>CAST(o.payload AS JSON)) OR
       NOT(m.aggregate_type<=>o.aggregate_type) OR NOT(m.aggregate_id<=>o.aggregate_id) OR NOT(m.event_type<=>o.event_type));
DROP TEMPORARY TABLE website_contract_guard;

-- With the source trigger still present, either trigger can complete the same guarded transition.
DELIMITER $$
CREATE TRIGGER admission_screen_change_parent AFTER UPDATE ON resume FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    IF NEW.status=5 THEN
      UPDATE interview_result ir
      SET ir.screened_draft=JSON_OBJECT('draftId',ir.result_id,
          'deptId',ir.draft_dept_id,'createdBy',ir.draft_created_by,'updatedBy',ir.draft_updated_by,
          'createdAt',ir.draft_created_at,'updatedAt',ir.draft_updated_at),
          ir.draft_dept_id=NULL,ir.draft_created_by=NULL,ir.draft_updated_by=NULL,
          ir.draft_created_at=NULL,ir.draft_updated_at=NULL,ir.updated_at=ir.updated_at
      WHERE ir.resume_id=NEW.resume_id AND ir.cycle_id=NEW.cycle_id AND ir.user_id=NEW.user_id AND ir.decision=0 AND ir.draft_dept_id IS NOT NULL;
    ELSEIF OLD.status=5 AND NEW.status IS NOT NULL THEN
      UPDATE interview_result ir
      SET ir.draft_dept_id=JSON_VALUE(ir.screened_draft,'$.deptId' RETURNING UNSIGNED),
          ir.draft_created_by=(SELECT cu.user_id FROM user cu WHERE cu.user_id=JSON_VALUE(ir.screened_draft,'$.createdBy' RETURNING UNSIGNED)),
          ir.draft_updated_by=(SELECT eu.user_id FROM user eu WHERE eu.user_id=JSON_VALUE(ir.screened_draft,'$.updatedBy' RETURNING UNSIGNED)),
          ir.draft_created_at=JSON_VALUE(ir.screened_draft,'$.createdAt' RETURNING DATETIME),
          ir.draft_updated_at=JSON_VALUE(ir.screened_draft,'$.updatedAt' RETURNING DATETIME),
          ir.screened_draft=NULL,ir.updated_at=ir.updated_at
      WHERE ir.resume_id=NEW.resume_id AND ir.cycle_id=NEW.cycle_id AND ir.user_id=NEW.user_id AND ir.decision=0
        AND EXISTS (SELECT 1 FROM department d WHERE d.dept_id=JSON_VALUE(ir.screened_draft,'$.deptId' RETURNING UNSIGNED));
    END IF;
  END IF;
END$$
DELIMITER ;
DROP TRIGGER admission_screen_change;
DROP TRIGGER admission_legacy_insert;
DROP TRIGGER admission_legacy_update;
DROP TRIGGER admission_legacy_delete;
DROP PROCEDURE mirror_admission_draft;
DROP VIEW admission_legacy_draft_route;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW delivery_sent_log AS
SELECT delivery_id AS id,notification_type,schedule_id,result_id,resume_id,request_id,recipient_email,
       notified_interview_time,mail_sent_at AS sent_at
FROM message_delivery WHERE mail_state='SENT';
CREATE OR REPLACE SQL SECURITY INVOKER VIEW delivery_cutover_gate AS SELECT 1 AS enabled;
DROP PROCEDURE import_legacy_delivery;
DROP VIEW interview_dept_choices_source,interview_time_choices_source,legacy_interview_slot_source,cycle_qr_source,parent_record_cutover;

UPDATE recruitment_cycle SET qr_codes=JSON_OBJECT(),updated_at=updated_at WHERE qr_codes IS NULL;
ALTER TABLE recruitment_cycle MODIFY qr_codes JSON NOT NULL DEFAULT (JSON_OBJECT());
UPDATE interview_preference SET accepted_time_slot_ids=JSON_ARRAY() WHERE accepted_time_slot_ids IS NULL;
ALTER TABLE interview_preference MODIFY accepted_time_slot_ids JSON NOT NULL DEFAULT (JSON_ARRAY());
ALTER TABLE interview_session MODIFY dept_id INT NULL DEFAULT NULL;
DROP TRIGGER interview_session_primary_insert;
DROP TRIGGER interview_session_primary_update;
ALTER TABLE interview_session DROP FOREIGN KEY fk_sess_dept, DROP INDEX idx_sess_dept,
    DROP COLUMN dept_id, MODIFY dept_ids JSON NOT NULL;
ALTER TABLE interview_schedule DROP FOREIGN KEY fk_schedule_slot, DROP INDEX idx_slot_id, DROP COLUMN slot_id;
DROP TABLE interview_preference_time,interview_session_dept,recruitment_qr_code,
    session_interviewer,pre_admission_draft,interview_slot,message_outbox,interview_notification_log;
SET SESSION time_zone=@contract_original_zone;
