-- Keep both versions' intake writes visible throughout the rolling release.
CREATE TRIGGER cycle_intake_compat_insert BEFORE INSERT ON recruitment_cycle FOR EACH ROW
SET NEW.intake_enabled=COALESCE(NEW.intake_enabled,NEW.is_active), NEW.is_active=NEW.intake_enabled;

CREATE TRIGGER cycle_intake_compat_update BEFORE UPDATE ON recruitment_cycle FOR EACH ROW
SET NEW.intake_enabled=IF(NEW.intake_enabled <=> OLD.intake_enabled, NEW.is_active, NEW.intake_enabled),
    NEW.is_active=NEW.intake_enabled;

UPDATE recruitment_cycle SET intake_enabled=is_active,updated_at=updated_at WHERE intake_enabled IS NULL;
ALTER TABLE recruitment_cycle ADD CONSTRAINT ck_cycle_intake_flag CHECK (intake_enabled IN (0,1));

-- Simple column aliases keep this compatibility view insertable and updatable.
CREATE ALGORITHM=MERGE SQL SECURITY INVOKER VIEW cycle_intake_config AS
SELECT cycle_id,cycle_name,description,start_date,end_date,academic_year,
       intake_enabled AS is_active,is_deleted,waiting_room,online_meeting_link,reschedule_open,
       contact_info,created_at,updated_at
FROM recruitment_cycle;

CREATE TRIGGER cycle_remove_legacy_tips BEFORE DELETE ON recruitment_cycle FOR EACH ROW
DELETE FROM recruitment_tips WHERE cycle_id=OLD.cycle_id;
