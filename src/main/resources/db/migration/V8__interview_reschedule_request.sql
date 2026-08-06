-- 面试改期申请：学生对已分配的面试时间提出改期，管理员审核后用「人工调剂」重排
CREATE TABLE IF NOT EXISTS interview_reschedule_request (
    request_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    schedule_id INT NOT NULL COMMENT '关联面试排期ID',
    resume_id INT NOT NULL COMMENT '关联简历ID',
    user_id INT NOT NULL COMMENT '申请人用户ID',
    cycle_id INT NOT NULL COMMENT '招募周期ID',
    reason VARCHAR(500) NOT NULL COMMENT '改期原因',
    preferred_time_slot_ids VARCHAR(200) NULL COMMENT '期望时间窗ID列表（逗号分隔，可空）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已同意 2已拒绝',
    admin_note VARCHAR(500) NULL COMMENT '管理员处理备注',
    handled_by INT NULL COMMENT '处理人用户ID',
    handled_at DATETIME NULL COMMENT '处理时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_cycle_status (cycle_id, status),
    KEY idx_user (user_id),
    KEY idx_schedule (schedule_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '面试改期申请';
