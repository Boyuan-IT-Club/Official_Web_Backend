ALTER TABLE interview_schedule
  ADD COLUMN legacy_slot_snapshot JSON NULL,
  ADD COLUMN notice_revision INT NOT NULL DEFAULT 1;
