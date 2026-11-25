-- Rolling upgrade: keep inputs accepted by both generations of the website.
ALTER TABLE `user` ALTER COLUMN role SET DEFAULT 'APPLICANT';
ALTER TABLE department ALTER COLUMN dept_code SET DEFAULT (REPLACE(UUID(),'-',''));
ALTER TABLE resume MODIFY COLUMN resume_score INT NULL DEFAULT NULL;

CREATE TRIGGER user_dept_compat_insert BEFORE INSERT ON `user` FOR EACH ROW
SET NEW.dept = IF(NEW.dept_id IS NULL, NEW.dept,
    (SELECT d.dept_name FROM department d WHERE d.dept_id=NEW.dept_id));

CREATE TRIGGER user_dept_compat_update BEFORE UPDATE ON `user` FOR EACH ROW
SET NEW.dept = IF(NEW.dept_id <=> OLD.dept_id, NEW.dept,
    (SELECT d.dept_name FROM department d WHERE d.dept_id=NEW.dept_id));

CREATE TRIGGER resume_scorer_compat_update BEFORE UPDATE ON resume FOR EACH ROW
SET NEW.scored_by = CASE
    WHEN NEW.scored_at IS NULL AND NEW.resume_score IS NULL THEN NULL
    ELSE COALESCE((SELECT e.scorer_id FROM resume_score_entry e WHERE e.resume_id=NEW.resume_id
                   ORDER BY e.updated_at DESC,e.id DESC LIMIT 1), NEW.scored_by)
END;
