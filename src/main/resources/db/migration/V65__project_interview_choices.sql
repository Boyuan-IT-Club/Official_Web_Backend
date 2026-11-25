-- Ordered window aggregation avoids truncation for large sets of time windows.
CREATE SQL SECURITY INVOKER VIEW interview_time_choices_source AS
SELECT resume_id,choices FROM (
    SELECT resume_id,
           JSON_ARRAYAGG(time_slot_id) OVER (PARTITION BY resume_id ORDER BY time_slot_id
               ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS choices,
           ROW_NUMBER() OVER (PARTITION BY resume_id ORDER BY time_slot_id) AS pos
    FROM interview_preference_time
) ordered_choices WHERE pos=1;

CREATE SQL SECURITY INVOKER VIEW interview_dept_choices_source AS
SELECT session_id,choices FROM (
    SELECT session_id,
           JSON_ARRAYAGG(dept_id) OVER (PARTITION BY session_id ORDER BY priority,id
               ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS choices,
           ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY priority,id) AS pos
    FROM (
        SELECT session_id,dept_id,0 AS priority,0 AS id FROM interview_session
        UNION ALL
        SELECT d.session_id,d.dept_id,1,d.id FROM interview_session_dept d
        JOIN interview_session s ON s.session_id=d.session_id AND s.dept_id<>d.dept_id
    ) members
) ordered_members WHERE pos=1;

CREATE TEMPORARY TABLE interview_choices_guard (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO interview_choices_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_preference_time x LEFT JOIN resume r USING(resume_id)
    LEFT JOIN interview_time_slot t USING(time_slot_id)
    WHERE r.resume_id IS NULL OR t.time_slot_id IS NULL OR NOT(r.cycle_id <=> t.cycle_id));
INSERT INTO interview_choices_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_preference p LEFT JOIN resume r USING(resume_id)
    WHERE r.resume_id IS NULL OR NOT(p.cycle_id <=> r.cycle_id));
INSERT INTO interview_choices_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_session s WHERE EXISTS (SELECT 1 FROM interview_session_dept d WHERE d.session_id=s.session_id)
    AND NOT EXISTS (SELECT 1 FROM interview_session_dept d WHERE d.session_id=s.session_id AND d.dept_id=s.dept_id));
INSERT INTO interview_choices_guard SELECT NOT EXISTS (
    SELECT 1 FROM interview_session_dept x JOIN interview_session s USING(session_id)
    LEFT JOIN department d ON d.dept_id=x.dept_id WHERE d.dept_id IS NULL);
DROP TEMPORARY TABLE interview_choices_guard;

INSERT INTO interview_preference(resume_id,cycle_id,submitted_at)
SELECT r.resume_id,r.cycle_id,NULL FROM resume r WHERE EXISTS (
    SELECT 1 FROM interview_preference_time x WHERE x.resume_id=r.resume_id)
AND NOT EXISTS (SELECT 1 FROM interview_preference p WHERE p.resume_id=r.resume_id);

UPDATE interview_preference p LEFT JOIN interview_time_choices_source v USING(resume_id)
SET p.accepted_time_slot_ids=COALESCE(v.choices,JSON_ARRAY()),p.updated_at=p.updated_at;
UPDATE interview_session s JOIN interview_dept_choices_source v USING(session_id)
SET s.dept_ids=v.choices,s.updated_at=s.updated_at;
