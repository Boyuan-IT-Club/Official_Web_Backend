ALTER TABLE interview_result
  ADD COLUMN draft_dept_id INT NULL,
  ADD COLUMN draft_created_by INT NULL,
  ADD COLUMN draft_updated_by INT NULL,
  ADD COLUMN draft_created_at DATETIME NULL,
  ADD COLUMN draft_updated_at DATETIME NULL,
  ADD COLUMN screened_draft JSON NULL COMMENT '初筛隐藏草稿的原始信息，用于恢复；有效草稿列保持空',
  ADD CONSTRAINT ck_result_screened_shape CHECK (
    screened_draft IS NULL OR JSON_TYPE(screened_draft)='OBJECT'),
  ADD INDEX idx_result_cycle_draft (cycle_id,draft_dept_id),
  ADD CONSTRAINT fk_result_draft_dept FOREIGN KEY (draft_dept_id) REFERENCES department(dept_id),
  ADD CONSTRAINT fk_result_draft_creator FOREIGN KEY (draft_created_by) REFERENCES `user`(user_id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_result_draft_editor FOREIGN KEY (draft_updated_by) REFERENCES `user`(user_id) ON DELETE SET NULL;
