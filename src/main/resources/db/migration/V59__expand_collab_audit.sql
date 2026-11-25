ALTER TABLE collab_audit
  MODIFY COLUMN user_id INT NULL,
  ADD COLUMN snapshot_conflict JSON NULL,
  ADD CONSTRAINT ck_collab_audit_actor CHECK (user_id IS NOT NULL OR
    (rejected=1 AND snapshot_conflict IS NOT NULL AND JSON_TYPE(snapshot_conflict)='OBJECT'));
