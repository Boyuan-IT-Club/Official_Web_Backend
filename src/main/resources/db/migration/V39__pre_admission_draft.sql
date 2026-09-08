-- 录取决策草稿（预录取名单）。
--
-- 草稿必须与 interview_result 分表：学生端进度和通知链路都只认
-- interview_result.decision，因此部门讨论、调剂期间不会提前泄露结果。
CREATE TABLE IF NOT EXISTS `pre_admission_draft` (
    `draft_id`         int      NOT NULL AUTO_INCREMENT,
    `cycle_id`         int      NOT NULL COMMENT '招募周期',
    `result_id`        int      NOT NULL COMMENT '候选人的面试结果行',
    `assigned_dept_id` int      NOT NULL COMMENT '拟录取部门',
    `created_by`       int      NULL COMMENT '首次加入人',
    `updated_by`       int      NULL COMMENT '最近调整人',
    `created_at`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`draft_id`),
    UNIQUE KEY `uk_pre_admission_result` (`result_id`),
    KEY `idx_pre_admission_cycle_dept` (`cycle_id`, `assigned_dept_id`),
    CONSTRAINT `fk_pre_admission_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pre_admission_result` FOREIGN KEY (`result_id`) REFERENCES `interview_result` (`result_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pre_admission_dept` FOREIGN KEY (`assigned_dept_id`) REFERENCES `department` (`dept_id`),
    CONSTRAINT `fk_pre_admission_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`user_id`) ON DELETE SET NULL,
    CONSTRAINT `fk_pre_admission_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='预录取名单（仅管理端可见的录取决策草稿）';
