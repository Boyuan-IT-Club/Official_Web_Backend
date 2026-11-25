ALTER TABLE recruitment_cycle
  ADD COLUMN intake_enabled TINYINT NULL,
  ADD COLUMN qr_codes JSON NULL;
