-- 老社员认领申请。
--
-- 场景：往届社员从没在官网注册过，注册后账号只是普通申请者身份，
-- 看到的是招新进度而不是社员视图。让他自己提交一份认领申请，
-- 管理员核对后一键置为社员并归部门，比管理员挨个去用户列表里找人改要可靠。
--
-- 为什么单独一张表而不是在 user 上加字段：认领是一次「申请-审批」事件，
-- 有提交内容、审批人、审批时间与驳回理由；user 上只留最终结果（is_member/dept_id）。

CREATE TABLE IF NOT EXISTS `member_claim` (
    `claim_id`     int          NOT NULL AUTO_INCREMENT,
    `user_id`      int          NOT NULL COMMENT '申请人',
    `real_name`    varchar(50)  NOT NULL COMMENT '真实姓名，用于和社团名册核对',
    `student_id`   varchar(32)  NULL     COMMENT '学号',
    `join_year`    varchar(32)  NULL     COMMENT '入社年份/届别，如 2024 或 2024级',
    `dept_id`      int          NULL     COMMENT '所属部门；通过时一并写回 user.dept_id',
    `evidence`     varchar(500) NULL     COMMENT '证明说明：担任过的职务、参与的项目、可作证的社员等',
    `status`       tinyint      NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
    `review_note`  varchar(255) NULL     COMMENT '审批备注/驳回理由',
    `reviewed_by`  int          NULL     COMMENT '审批人 userId',
    `reviewed_at`  datetime     NULL,
    `created_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`claim_id`),
    -- 一人同时只能有一条待审申请：靠应用层判重 + 这里的索引加速查询。
    -- 不做唯一键，因为被驳回后应当允许补充材料重新申请。
    KEY `idx_claim_user` (`user_id`, `status`),
    KEY `idx_claim_status` (`status`, `created_at`),
    CONSTRAINT `fk_claim_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_claim_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`dept_id`),
    CONSTRAINT `fk_claim_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `user` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='老社员认领申请';
