-- 录取结果变更留痕。
--
-- 背景：结果定了以后还能被批量覆盖、单行改来改去，且改了什么、谁改的、为什么改，
-- 系统里一点痕迹都没有——应用日志分散在双机两台机器上，事后无从回溯。
-- 从这版起，已定的结果（通过 / 未通过）只能走「更正」，必须填原因，每次变动都写一行。
--
-- result_id 刻意不建外键：删除简历会级联删除结果行，留痕要能在那之后保住。
CREATE TABLE IF NOT EXISTS `interview_result_change`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `result_id`     int          NOT NULL COMMENT '面试结果ID（不建外键，结果行删除后留痕仍在）',
    `cycle_id`      int          NULL COMMENT '招募周期ID',
    `action`        varchar(20)  NOT NULL COMMENT 'DECIDE 首次定 / CORRECT 更正 / BATCH 批量 / FINALIZE 预录取转正 / FEISHU 飞书 / MIGRATION 迁移',
    `from_decision` tinyint      NULL COMMENT '改前结果：0待定 1通过 2未通过 3待调剂',
    `from_dept_id`  int          NULL COMMENT '改前录取部门',
    `to_decision`   tinyint      NULL COMMENT '改后结果',
    `to_dept_id`    int          NULL COMMENT '改后录取部门',
    `was_notified`  tinyint      NOT NULL DEFAULT 0 COMMENT '改动时是否已发过结果通知（已通知的更正要补发）',
    `reason`        varchar(255) NULL COMMENT '更正原因；首次定结果可空',
    `operator_id`   int          NULL COMMENT '操作人 user_id；系统动作为空',
    `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_result` (`result_id`),
    KEY `idx_cycle_time` (`cycle_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='录取结果变更留痕';
