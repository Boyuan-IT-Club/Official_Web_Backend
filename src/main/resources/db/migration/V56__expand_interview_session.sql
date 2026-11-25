ALTER TABLE interview_session ADD COLUMN dept_ids JSON NULL,
  ADD CONSTRAINT ck_session_depts CHECK (dept_ids IS NULL OR JSON_SCHEMA_VALID(
    '{"type":"array","minItems":1,"uniqueItems":true,"items":{"type":"integer","minimum":1,"maximum":2147483647}}',dept_ids));
