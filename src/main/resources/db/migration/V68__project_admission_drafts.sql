CREATE SQL SECURITY INVOKER VIEW admission_legacy_draft_route AS
SELECT p.draft_id,
  CASE
    WHEN ir.result_id IS NULL THEN 'ARCHIVE_RESULT'
    WHEN ir.resume_id IS NULL OR sr.resume_id IS NULL THEN 'ARCHIVE_RESUME'
    WHEN ir.cycle_id IS NULL OR sr.cycle_id IS NULL OR ir.cycle_id<>p.cycle_id OR sr.cycle_id<>p.cycle_id THEN 'ARCHIVE_CYCLE'
    WHEN NOT (sr.user_id <=> ir.user_id) THEN 'ARCHIVE_USER'
    WHEN ir.decision IS NULL OR ir.decision<>0 THEN 'ARCHIVE_DECISION'
    WHEN sr.status IS NULL THEN 'ARCHIVE_STATUS'
    WHEN d.dept_id IS NULL THEN 'ARCHIVE_DEPT'
    WHEN (p.created_by IS NOT NULL AND cu.user_id IS NULL) OR
         (p.updated_by IS NOT NULL AND eu.user_id IS NULL) THEN 'ARCHIVE_ACTOR'
    WHEN sr.status=5 THEN 'HIDDEN'
    ELSE 'ACTIVE'
  END AS route
FROM pre_admission_draft p
LEFT JOIN interview_result ir ON ir.result_id=p.result_id
LEFT JOIN resume sr ON sr.resume_id=ir.resume_id
LEFT JOIN department d ON d.dept_id=p.assigned_dept_id
LEFT JOIN `user` cu ON cu.user_id=p.created_by
LEFT JOIN `user` eu ON eu.user_id=p.updated_by;

CREATE SQL SECURITY INVOKER VIEW admission_active_draft AS
SELECT ir.result_id AS draft_id, ir.cycle_id, ir.result_id, ir.draft_dept_id AS assigned_dept_id,
       ir.draft_created_by AS created_by, ir.draft_updated_by AS updated_by,
       ir.draft_created_at AS created_at, ir.draft_updated_at AS updated_at, ir.user_id
FROM interview_result ir JOIN resume sr ON sr.resume_id=ir.resume_id AND sr.cycle_id=ir.cycle_id AND sr.user_id=ir.user_id
WHERE ir.decision=0 AND sr.status<>5 AND ir.draft_dept_id IS NOT NULL AND ir.screened_draft IS NULL;

DELIMITER $$
CREATE PROCEDURE mirror_admission_draft(IN src_id INT, IN result_key INT, IN cycle_key INT, IN department_key INT,
    IN creator INT, IN editor INT, IN created_time DATETIME, IN updated_time DATETIME)
BEGIN
  UPDATE interview_result ir JOIN resume sr ON sr.resume_id=ir.resume_id AND sr.cycle_id=ir.cycle_id AND sr.user_id=ir.user_id
  SET ir.draft_dept_id=IF(sr.status=5,NULL,department_key), ir.draft_created_by=IF(sr.status=5,NULL,creator),
      ir.draft_updated_by=IF(sr.status=5,NULL,editor), ir.draft_created_at=IF(sr.status=5,NULL,created_time),
      ir.draft_updated_at=IF(sr.status=5,NULL,updated_time),
      ir.screened_draft=IF(sr.status=5,JSON_OBJECT('draftId',src_id,'deptId',department_key,'createdBy',creator,
          'updatedBy',editor,'createdAt',created_time,'updatedAt',updated_time),NULL), ir.updated_at=ir.updated_at
  WHERE ir.result_id=result_key AND ir.cycle_id=cycle_key AND ir.decision=0 AND sr.status IS NOT NULL;
END$$
CREATE TRIGGER admission_legacy_insert AFTER INSERT ON pre_admission_draft FOR EACH ROW
  CALL mirror_admission_draft(NEW.draft_id,NEW.result_id,NEW.cycle_id,NEW.assigned_dept_id,
       NEW.created_by,NEW.updated_by,NEW.created_at,NEW.updated_at)$$
CREATE TRIGGER admission_legacy_update AFTER UPDATE ON pre_admission_draft FOR EACH ROW
  CALL mirror_admission_draft(NEW.draft_id,NEW.result_id,NEW.cycle_id,NEW.assigned_dept_id,
       NEW.created_by,NEW.updated_by,NEW.created_at,NEW.updated_at)$$
CREATE TRIGGER admission_legacy_delete AFTER DELETE ON pre_admission_draft FOR EACH ROW
  UPDATE interview_result SET draft_dept_id=NULL,draft_created_by=NULL,draft_updated_by=NULL,
     draft_created_at=NULL,draft_updated_at=NULL,screened_draft=NULL, updated_at=updated_at
  WHERE result_id=OLD.result_id AND cycle_id=OLD.cycle_id$$

CREATE TRIGGER admission_clear_decided BEFORE UPDATE ON interview_result FOR EACH ROW
BEGIN
  IF NEW.decision<>0 THEN
    SET NEW.draft_dept_id=NULL, NEW.draft_created_by=NULL, NEW.draft_updated_by=NULL,
        NEW.draft_created_at=NULL, NEW.draft_updated_at=NULL, NEW.screened_draft=NULL;
  END IF;
END$$

CREATE TRIGGER admission_screen_change AFTER UPDATE ON resume FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    IF NEW.status=5 THEN
      UPDATE interview_result ir
      SET ir.screened_draft=JSON_OBJECT('draftId',COALESCE((SELECT p.draft_id FROM pre_admission_draft p WHERE p.result_id=ir.result_id),ir.result_id),
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

UPDATE interview_result r JOIN pre_admission_draft p ON p.result_id=r.result_id
      JOIN admission_legacy_draft_route v ON v.draft_id=p.draft_id
    SET r.draft_dept_id=p.assigned_dept_id,r.draft_created_by=p.created_by,r.draft_updated_by=p.updated_by,
      r.draft_created_at=p.created_at,r.draft_updated_at=p.updated_at,r.updated_at=r.updated_at
    WHERE v.route='ACTIVE' AND r.draft_dept_id IS NULL AND r.screened_draft IS NULL;

    UPDATE interview_result r JOIN pre_admission_draft p ON p.result_id=r.result_id
      JOIN admission_legacy_draft_route v ON v.draft_id=p.draft_id
    SET r.screened_draft=JSON_OBJECT('draftId',p.draft_id,'deptId',p.assigned_dept_id,
      'createdBy',p.created_by,'updatedBy',p.updated_by,'createdAt',p.created_at,'updatedAt',p.updated_at),
      r.updated_at=r.updated_at
    WHERE v.route='HIDDEN' AND r.draft_dept_id IS NULL AND r.screened_draft IS NULL;
