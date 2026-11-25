ALTER TABLE interview_preference ADD COLUMN accepted_time_slot_ids JSON NULL,
  ADD CONSTRAINT ck_pref_times CHECK (accepted_time_slot_ids IS NULL OR JSON_SCHEMA_VALID(
    '{"type":"array","uniqueItems":true,"items":{"type":"integer","minimum":1,"maximum":2147483647}}',accepted_time_slot_ids));
