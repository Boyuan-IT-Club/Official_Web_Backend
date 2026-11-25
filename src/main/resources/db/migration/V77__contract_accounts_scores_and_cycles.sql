-- Both website nodes already use role mappings, dept_id, scored entries and cycle_intake_config.
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE cycle_contract_guard (ok TINYINT NOT NULL CHECK(ok=1));
INSERT INTO cycle_contract_guard SELECT NOT EXISTS (SELECT 1 FROM recruitment_cycle
    WHERE intake_enabled IS NULL OR intake_enabled NOT IN (0,1) OR intake_enabled<>is_active);
DROP TEMPORARY TABLE cycle_contract_guard;

DROP TRIGGER user_dept_compat_insert;
DROP TRIGGER user_dept_compat_update;
DROP TRIGGER resume_scorer_compat_update;
ALTER TABLE `user` DROP COLUMN role, DROP COLUMN dept;
ALTER TABLE department DROP INDEX uk_dept_code, DROP COLUMN dept_code;
ALTER TABLE permission DROP INDEX idx_resource_identifier, DROP COLUMN resource_identifier;
ALTER TABLE resume DROP COLUMN scored_by;

DROP TRIGGER cycle_remove_legacy_tips;
DROP TABLE recruitment_tips;

-- The existing updatable view stays available while status changes meaning.
ALTER TABLE recruitment_cycle MODIFY intake_enabled TINYINT NOT NULL DEFAULT 1;
DROP TRIGGER cycle_intake_compat_insert;
DROP TRIGGER cycle_intake_compat_update;
ALTER TABLE recruitment_cycle RENAME COLUMN status TO legacy_phase,
    ADD COLUMN status TINYINT NULL DEFAULT NULL;
CREATE TRIGGER cycle_status_bridge_insert BEFORE INSERT ON recruitment_cycle FOR EACH ROW
SET NEW.status=COALESCE(NEW.status,NEW.intake_enabled),NEW.intake_enabled=NEW.status;
CREATE TRIGGER cycle_status_bridge_update BEFORE UPDATE ON recruitment_cycle FOR EACH ROW
SET NEW.status=IF(NEW.status <=> OLD.status,NEW.intake_enabled,NEW.status),NEW.intake_enabled=NEW.status;
UPDATE recruitment_cycle SET status=intake_enabled,updated_at=updated_at WHERE status IS NULL;
CREATE OR REPLACE ALGORITHM=MERGE SQL SECURITY INVOKER VIEW cycle_intake_config AS
SELECT cycle_id,cycle_name,description,start_date,end_date,academic_year,status AS is_active,
       is_deleted,waiting_room,online_meeting_link,reschedule_open,contact_info,created_at,updated_at
FROM recruitment_cycle;
ALTER TABLE recruitment_cycle MODIFY status TINYINT NOT NULL DEFAULT 1 COMMENT '是否接受投递';
DROP TRIGGER cycle_status_bridge_insert;
DROP TRIGGER cycle_status_bridge_update;
ALTER TABLE recruitment_cycle DROP CHECK ck_cycle_intake_flag, DROP COLUMN intake_enabled,
    DROP COLUMN is_active,DROP COLUMN legacy_phase,ADD CONSTRAINT ck_cycle_intake_status CHECK(status IN (0,1));
