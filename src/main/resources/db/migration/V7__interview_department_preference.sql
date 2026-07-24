-- ===========================================================================
-- V7：面试"志愿部门 + 时间窗 + 场次分配"（方案B）
-- 学生填至多两个志愿部门 + 勾选一个或多个大时段；管理员维护场次(部门×时间窗×地点×容量)；
-- 算法把每人分配到唯一一个场次（第一志愿满降级第二志愿，都不行进待调剂），并细分精确时间。
-- ===========================================================================
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 面试时间窗（"我们提供的时间"，学生勾选，如"周六上午"）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_time_slot`
(
    `time_slot_id`   int          NOT NULL AUTO_INCREMENT COMMENT '时间窗ID',
    `cycle_id`       int          NOT NULL COMMENT '招募周期ID',
    `slot_name`      varchar(100) NOT NULL COMMENT '时段名称，如 周六上午',
    `interview_date` date         NOT NULL COMMENT '面试日期',
    `start_time`     time         NOT NULL COMMENT '开始时间',
    `end_time`       time         NOT NULL COMMENT '结束时间',
    `status`         tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1(可选), 2(关闭)',
    `created_at`     timestamp    NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`time_slot_id`),
    INDEX `idx_ts_cycle` (`cycle_id`),
    CONSTRAINT `fk_ts_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='面试时间窗（学生可勾选的大时段）';

-- ---------------------------------------------------------------------------
-- 面试场次（部门 × 时间窗 × 地点 × 容量）
--   同一时间窗同一地点多部门 → 多条 session（同 time_slot_id、同 location、不同 dept_id）
--   同一时间窗不同地点        → 多条 session（同 time_slot_id、不同 location）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_session`
(
    `session_id`                 int          NOT NULL AUTO_INCREMENT COMMENT '场次ID',
    `cycle_id`                   int          NOT NULL COMMENT '招募周期ID',
    `time_slot_id`               int          NOT NULL COMMENT '时间窗ID',
    `dept_id`                    int          NOT NULL COMMENT '部门ID',
    `location`                   varchar(255) NOT NULL COMMENT '面试地点',
    `capacity`                   int          NOT NULL DEFAULT 10 COMMENT '容量（该场次可面人数）',
    `current_occupied`           int          NOT NULL DEFAULT 0 COMMENT '已分配人数',
    `interview_duration_minutes` int          NOT NULL DEFAULT 10 COMMENT '单人面试时长(分钟)',
    `status`                     tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1(可用), 2(关闭)',
    `created_at`                 timestamp    NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`session_id`),
    INDEX `idx_sess_cycle` (`cycle_id`),
    INDEX `idx_sess_slot` (`time_slot_id`),
    INDEX `idx_sess_dept` (`dept_id`),
    CONSTRAINT `fk_sess_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sess_slot` FOREIGN KEY (`time_slot_id`) REFERENCES `interview_time_slot` (`time_slot_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sess_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='面试场次(部门×时间窗×地点×容量)';

-- ---------------------------------------------------------------------------
-- 学生志愿（第一/第二志愿部门），一份简历一条
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_preference`
(
    `preference_id`  int       NOT NULL AUTO_INCREMENT COMMENT '志愿ID',
    `resume_id`      int       NOT NULL COMMENT '简历ID',
    `cycle_id`       int       NOT NULL COMMENT '招募周期ID',
    `first_dept_id`  int       NULL COMMENT '第一志愿部门',
    `second_dept_id` int       NULL COMMENT '第二志愿部门',
    `submitted_at`   timestamp NULL COMMENT '提交时间',
    `created_at`     timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`preference_id`),
    UNIQUE INDEX `uk_pref_resume` (`resume_id`),
    INDEX `idx_pref_cycle` (`cycle_id`),
    CONSTRAINT `fk_pref_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pref_first_dept` FOREIGN KEY (`first_dept_id`) REFERENCES `department` (`dept_id`),
    CONSTRAINT `fk_pref_second_dept` FOREIGN KEY (`second_dept_id`) REFERENCES `department` (`dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='学生面试志愿部门';

-- ---------------------------------------------------------------------------
-- 学生可接受的时间窗（多选，m:n）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_preference_time`
(
    `id`           int       NOT NULL AUTO_INCREMENT,
    `resume_id`    int       NOT NULL COMMENT '简历ID',
    `time_slot_id` int       NOT NULL COMMENT '时间窗ID',
    `created_at`   timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pref_time` (`resume_id`, `time_slot_id`),
    INDEX `idx_preftime_slot` (`time_slot_id`),
    CONSTRAINT `fk_preftime_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_preftime_slot` FOREIGN KEY (`time_slot_id`) REFERENCES `interview_time_slot` (`time_slot_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='学生可接受的面试时间窗';

-- ---------------------------------------------------------------------------
-- 面试安排表接入场次维度：新增 session_id / dept_id；旧自助 slot_id 放开为可空
-- ---------------------------------------------------------------------------
ALTER TABLE `interview_schedule`
    ADD COLUMN `session_id` int NULL COMMENT '面试场次ID（方案B）' AFTER `slot_id`;
ALTER TABLE `interview_schedule`
    ADD COLUMN `dept_id` int NULL COMMENT '面试部门ID（冗余，来自 session）' AFTER `session_id`;
ALTER TABLE `interview_schedule`
    MODIFY COLUMN `slot_id` int NULL COMMENT '旧自助时段ID（已弃用，方案B下为空）';
ALTER TABLE `interview_schedule`
    ADD INDEX `idx_schedule_session` (`session_id`);
ALTER TABLE `interview_schedule`
    ADD CONSTRAINT `fk_schedule_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`session_id`) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 历史志愿部门迁移（best-effort）：简历动态字段"期望部门"(JSON 数组，如 ["技术部","综合部"])
-- 按部门名映射到 dept_id；解析不了/名字对不上则留空。历史"期望时间"因需对应新的时间窗，故不迁移。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `interview_preference` (`resume_id`, `cycle_id`, `first_dept_id`, `second_dept_id`, `submitted_at`)
SELECT r.`resume_id`,
       r.`cycle_id`,
       d1.`dept_id`,
       d2.`dept_id`,
       r.`submitted_at`
FROM `resume` r
         JOIN `resume_field_definition` fd
              ON fd.`cycle_id` = r.`cycle_id` AND fd.`field_label` = '期望部门'
         JOIN `resume_field_value` fv
              ON fv.`resume_id` = r.`resume_id` AND fv.`field_id` = fd.`field_id`
         LEFT JOIN `department` d1
                   ON d1.`dept_name` = JSON_UNQUOTE(JSON_EXTRACT(fv.`field_value`, '$[0]'))
         LEFT JOIN `department` d2
                   ON d2.`dept_name` = JSON_UNQUOTE(JSON_EXTRACT(fv.`field_value`, '$[1]'))
WHERE JSON_VALID(fv.`field_value`);

SET FOREIGN_KEY_CHECKS = 1;
