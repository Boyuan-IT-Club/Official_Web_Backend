-- V6：核心业务表基线（幂等）。
-- 背景：历史上核心表由 Hibernate ddl-auto=update 隐式建表，Flyway 仅维护增量（V2 outbox、V3~V5 面试相关列/表），
--       导致「全新空库仅靠 Flyway 迁移」会缺失 user/role/resume/interview_* 等约 16 张核心表。
-- 目的：把完整核心 schema 落入 Flyway，使空库可仅凭迁移建成可用结构，同时对已存在的库完全无副作用。
-- 幂等性：全部 CREATE TABLE IF NOT EXISTS + INSERT IGNORE；已有表/数据不会被改动或重复插入。
-- 注意：不含 message_outbox（V2 负责）与 interview_notification_log（V4/V5 负责）。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 招募活动表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `recruitment_cycle`
(
    `cycle_id`      int          NOT NULL AUTO_INCREMENT COMMENT '招募活动ID',
    `cycle_name`    varchar(100) NOT NULL COMMENT '招募活动名称',
    `description`   text COMMENT '活动基本介绍',
    `start_date`    date         NOT NULL COMMENT '活动开始日期',
    `end_date`      date         NOT NULL COMMENT '活动截止日期',
    `academic_year` varchar(20)  NOT NULL COMMENT '学年',
    `status`        tinyint      NOT NULL DEFAULT '1' COMMENT '活动状态：1(未开始), 2(进行中), 3(已结束), 4(已关闭)',
    `is_active`     tinyint(1)   NOT NULL DEFAULT '1' COMMENT '是否启用：0(禁用), 1(启用)',
    `created_at`    timestamp    NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    timestamp    NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`cycle_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='招募活动表';

-- ---------------------------------------------------------------------------
-- 部门表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `department`
(
    `dept_id`     int          NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    `dept_name`   varchar(50)  NOT NULL COMMENT '部门名称',
    `dept_code`   varchar(50)  NOT NULL COMMENT '部门编码',
    `description` varchar(255) NULL DEFAULT NULL COMMENT '部门描述',
    `status`      tinyint(1)   NOT NULL DEFAULT 1 COMMENT '部门状态：0(禁用), 1(启用)',
    `create_time` timestamp    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`dept_id`) USING BTREE,
    UNIQUE INDEX `uk_dept_code` (`dept_code` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门表';

-- ---------------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user`
(
    `user_id`     int          NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    varchar(50)  NOT NULL COMMENT '用户名',
    `password`    varchar(255) NOT NULL COMMENT '密码(加密存储)',
    `name`        varchar(50)  NULL DEFAULT NULL COMMENT '真实姓名',
    `email`       varchar(100) NOT NULL COMMENT '邮箱地址',
    `phone`       varchar(20)  NULL DEFAULT NULL COMMENT '手机号码',
    `major`       varchar(100) NULL DEFAULT NULL COMMENT '专业',
    `github`      varchar(255) NULL DEFAULT NULL COMMENT 'GitHub地址',
    `dept_id`     int          NULL DEFAULT NULL COMMENT '所属部门ID',
    `avatar`      varchar(255) NULL DEFAULT NULL COMMENT '头像URL',
    `status`      tinyint(1)   NOT NULL DEFAULT 1 COMMENT '用户状态：0(禁用), 1(启用)',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '删除标记：0(未删除), 1(已删除)',
    `create_time` timestamp    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`) USING BTREE,
    UNIQUE INDEX `uk_username` (`username` ASC) USING BTREE,
    UNIQUE INDEX `uk_email` (`email` ASC) USING BTREE,
    INDEX `idx_dept_id` (`dept_id` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE,
    INDEX `idx_create_time` (`create_time` ASC) USING BTREE,
    CONSTRAINT `fk_user_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`dept_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- ---------------------------------------------------------------------------
-- 角色表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role`
(
    `role_id`     int          NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`   varchar(50)  NOT NULL COMMENT '角色名称',
    `role_code`   varchar(50)  NOT NULL COMMENT '角色编码',
    `description` varchar(255) NULL DEFAULT NULL COMMENT '角色描述',
    `status`      tinyint(1)   NOT NULL DEFAULT 1 COMMENT '角色状态：0(禁用), 1(启用)',
    `create_time` timestamp    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`role_id`) USING BTREE,
    UNIQUE INDEX `uk_role_code` (`role_code` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表';

-- ---------------------------------------------------------------------------
-- 权限表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission`
(
    `permission_id`       int          NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `permission_name`     varchar(100) NOT NULL COMMENT '权限名称',
    `permission_code`     varchar(100) NOT NULL COMMENT '权限编码',
    `resource_identifier` varchar(255) NULL DEFAULT NULL COMMENT '资源标识符',
    `description`         varchar(255) NULL DEFAULT NULL COMMENT '权限描述',
    `create_time`         timestamp    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`permission_id`) USING BTREE,
    UNIQUE INDEX `uk_permission_code` (`permission_code` ASC) USING BTREE,
    INDEX `idx_resource_identifier` (`resource_identifier` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限表';

-- ---------------------------------------------------------------------------
-- 用户角色关联表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role`
(
    `user_role_id` int       NOT NULL AUTO_INCREMENT COMMENT '用户角色关联ID',
    `user_id`      int       NOT NULL COMMENT '用户ID',
    `role_id`      int       NOT NULL COMMENT '角色ID',
    `create_time`  timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_role_id`) USING BTREE,
    UNIQUE INDEX `uk_user_role` (`user_id`, `role_id`) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_role_id` (`role_id` ASC) USING BTREE,
    CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表';

-- ---------------------------------------------------------------------------
-- 角色权限关联表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission`
(
    `role_permission_id` int       NOT NULL AUTO_INCREMENT COMMENT '角色权限关联ID',
    `role_id`            int       NOT NULL COMMENT '角色ID',
    `permission_id`      int       NOT NULL COMMENT '权限ID',
    `create_time`        timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`role_permission_id`) USING BTREE,
    UNIQUE INDEX `uk_role_permission` (`role_id`, `permission_id`) USING BTREE,
    INDEX `idx_role_id` (`role_id` ASC) USING BTREE,
    INDEX `idx_permission_id` (`permission_id` ASC) USING BTREE,
    CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`permission_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表';

-- ---------------------------------------------------------------------------
-- 获奖经历表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `award_experience`
(
    `award_id`    int          NOT NULL AUTO_INCREMENT,
    `user_id`     int          NOT NULL,
    `award_name`  varchar(255) NOT NULL,
    `award_time`  date         NOT NULL,
    `description` text         NULL,
    `created_at`  timestamp    NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`award_id`) USING BTREE,
    INDEX `user_id` (`user_id` ASC) USING BTREE,
    CONSTRAINT `award_experience_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 简历表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume`
(
    `resume_id`    int       NOT NULL AUTO_INCREMENT,
    `user_id`      int       NOT NULL,
    `cycle_id`     int       NOT NULL,
    `status`       tinyint   NOT NULL DEFAULT 1 COMMENT '简历状态: 1(草稿), 2(已提交), 3(评审中), 4(通过), 5(未通过)',
    `resume_score` int       NOT NULL DEFAULT 0 COMMENT '简历得分',
    `submitted_at` timestamp NULL     DEFAULT NULL COMMENT '提交时间',
    `created_at`   timestamp NULL     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   timestamp NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`resume_id`) USING BTREE,
    UNIQUE INDEX `uk_user_cycle` (`user_id`, `cycle_id`) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_cycle_id` (`cycle_id` ASC) USING BTREE,
    CONSTRAINT `resume_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `resume_ibfk_2` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 简历字段定义表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_field_definition`
(
    `field_id`    int          NOT NULL AUTO_INCREMENT,
    `cycle_id`    int          NOT NULL,
    `field_key`   varchar(255) NOT NULL,
    `field_label` varchar(255) NOT NULL,
    `field_type`  varchar(50)  DEFAULT 'text' COMMENT '字段类型：text, textarea, select, radio, checkbox, file',
    `placeholder` varchar(255) COMMENT '字段描述/占位符',
    `is_required` tinyint(1)   NULL DEFAULT 0,
    `sort_order`  int          NULL DEFAULT 0,
    `is_active`   tinyint(1)   NULL DEFAULT 1,
    `created_at`  timestamp    NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`field_id`) USING BTREE,
    UNIQUE INDEX `uk_cycle_field_key` (`cycle_id` ASC, `field_key` ASC) USING BTREE,
    INDEX `idx_cycle_id` (`cycle_id` ASC) USING BTREE,
    CONSTRAINT `resume_field_definition_ibfk_1` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 简历字段值表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_field_value`
(
    `value_id`    int        NOT NULL AUTO_INCREMENT,
    `resume_id`   int        NOT NULL,
    `field_id`    int        NOT NULL,
    `field_value` mediumtext NULL,
    `created_at`  timestamp  NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  timestamp  NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`value_id`) USING BTREE,
    UNIQUE INDEX `uk_resume_field` (`resume_id` ASC, `field_id` ASC) USING BTREE,
    INDEX `resume_id` (`resume_id` ASC) USING BTREE,
    INDEX `field_id` (`field_id` ASC) USING BTREE,
    CONSTRAINT `resume_field_value_ibfk_1` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `resume_field_value_ibfk_2` FOREIGN KEY (`field_id`) REFERENCES `resume_field_definition` (`field_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 面试时段配置表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_slot`
(
    `slot_id`          int          NOT NULL AUTO_INCREMENT COMMENT '分配ID',
    `cycle_id`         int          NOT NULL COMMENT '招募活动ID',
    `interview_date`   date         NOT NULL COMMENT '面试日期',
    `start_time`       time         NOT NULL COMMENT '开始时间',
    `end_time`         time         NOT NULL COMMENT '结束时间',
    `location`         varchar(255) COMMENT '面试地点',
    `interview_type`   tinyint      NOT NULL DEFAULT 1 COMMENT '面试类型：1(线下面试), 2(线上面试)',
    `meeting_link`     varchar(500) NULL COMMENT '会议链接（线上面试用）',
    `max_capacity`     int          NOT NULL DEFAULT 10 COMMENT '最大容量',
    `current_occupied` int          NOT NULL DEFAULT 0 COMMENT '当前已占用人数',
    `feishu_table_url` varchar(255) DEFAULT NULL COMMENT '飞书多维表格URL',
    `status`           tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1(可用), 2(已满), 3(关闭)',
    `created_at`       timestamp    NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`slot_id`),
    INDEX `idx_cycle_date` (`cycle_id`, `interview_date`),
    INDEX `idx_status` (`status`),
    CONSTRAINT `fk_slot_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='面试时段配置表';

-- ---------------------------------------------------------------------------
-- 面试安排表（已含 feishu_record_id / notif_status，避免空库时 V3/V5 因表不存在而漏加列）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_schedule`
(
    `schedule_id`      int         NOT NULL AUTO_INCREMENT COMMENT '面试安排ID',
    `resume_id`        int         NOT NULL COMMENT '简历ID',
    `cycle_id`         int         NOT NULL COMMENT '招募活动ID',
    `slot_id`          int         NOT NULL COMMENT '分配ID',
    `interview_time`   datetime    NULL COMMENT '分配的面试具体时间',
    `status`           tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0（未安排），1(已安排), 2(已取消)',
    `notes`            text        NULL COMMENT '安排备注',
    `sync_status`      tinyint     NOT NULL DEFAULT 0 COMMENT '同步飞书状态：0(未同步), 1(已同步)',
    `feishu_record_id` varchar(64) DEFAULT NULL COMMENT '飞书多维表格行 record_id',
    `notif_status`     tinyint     NOT NULL DEFAULT 0 COMMENT '通知状态：0(未通知), 1(已通知)',
    `created_at`       timestamp   NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       timestamp   NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`schedule_id`),
    UNIQUE INDEX `uk_resume_cycle` (`resume_id`, `cycle_id`),
    INDEX `idx_slot_id` (`slot_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_schedule_feishu_record` (`feishu_record_id`),
    CONSTRAINT `fk_schedule_slot` FOREIGN KEY (`slot_id`) REFERENCES `interview_slot` (`slot_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_schedule_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_schedule_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='面试安排表';

-- ---------------------------------------------------------------------------
-- 面试结果表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_result`
(
    `result_id`        int       NOT NULL AUTO_INCREMENT COMMENT '结果ID',
    `schedule_id`      int       NOT NULL COMMENT '面试安排ID',
    `user_id`          int       NOT NULL COMMENT '用户ID',
    `decision`         tinyint   NOT NULL DEFAULT 0 COMMENT '最终决定：0(待定), 1(通过), 2(不通过), 3(待调剂)',
    `assigned_dept_id` int       NULL COMMENT '实际分配部门ID',
    `decision_by`      int       NULL COMMENT '决定人ID',
    `decision_at`      timestamp NULL COMMENT '决定时间',
    `created_at`       timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`result_id`),
    UNIQUE INDEX `uk_schedule` (`schedule_id`),
    INDEX `idx_decision` (`decision`),
    CONSTRAINT `fk_result_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `interview_schedule` (`schedule_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_result_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
    CONSTRAINT `fk_result_assigned_dept` FOREIGN KEY (`assigned_dept_id`) REFERENCES `department` (`dept_id`),
    CONSTRAINT `fk_result_decision_by` FOREIGN KEY (`decision_by`) REFERENCES `user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='面试结果表';

-- ---------------------------------------------------------------------------
-- 社团活动表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `activity`
(
    `activity_id`          int          NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `title`                varchar(100) NOT NULL COMMENT '活动标题',
    `description`          text COMMENT '活动描述',
    `category`             varchar(20) COMMENT '活动类别',
    `cover_image`          varchar(255) COMMENT '封面图片',
    `start_time`           date         NOT NULL COMMENT '开始时间',
    `end_time`             date         NOT NULL COMMENT '结束时间',
    `signup_start`         date COMMENT '报名开始时间',
    `signup_deadline`      date COMMENT '报名截止时间',
    `location`             varchar(100) COMMENT '活动地点',
    `max_participants`     int       DEFAULT 0 COMMENT '最大参与人数',
    `current_participants` int       DEFAULT 0 COMMENT '当前参与人数',
    `status`               tinyint   NOT NULL DEFAULT 0 COMMENT '活动状态（0-未开始，1-进行中，2-已结束，3-已取消）',
    `is_featured`          tinyint(1) DEFAULT 0 COMMENT '是否推荐活动',
    `cycle_sequence`       int       DEFAULT 0 COMMENT '周期序列',
    `created_at`           timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`activity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='社团活动表';

-- ---------------------------------------------------------------------------
-- 招新填写提示表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `recruitment_tips`
(
    `tip_id`     int          NOT NULL AUTO_INCREMENT COMMENT '提示ID',
    `cycle_id`   int          NOT NULL COMMENT '招募活动ID',
    `title`      varchar(255) NOT NULL COMMENT '提示标题',
    `content`    text         NOT NULL COMMENT '提示内容',
    `sort_order` int          NOT NULL DEFAULT 0 COMMENT '排序顺序',
    `is_active`  tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` timestamp    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`tip_id`),
    INDEX `idx_cycle_id` (`cycle_id`),
    CONSTRAINT `recruitment_tips_ibfk_1` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='招新填写提示表';

-- ===========================================================================
-- 基础参考数据（幂等：INSERT IGNORE，已有库不受影响）
-- ===========================================================================
INSERT IGNORE INTO `recruitment_cycle` (`cycle_id`, `cycle_name`, `description`, `start_date`, `end_date`, `academic_year`, `status`, `is_active`, `created_at`, `updated_at`)
VALUES (1, '2024年秋季招新', '华东师范大学博远社团2024年秋季招新活动，欢迎各位同学加入我们的大家庭！', '2024-09-01', '2024-09-30', '2024-2025学年', 3, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00'),
       (2, '2025年秋季招新', '华东师范大学博远社团2025年秋季招新活动，寻找志同道合的你！', '2025-09-01', '2025-09-30', '2025-2026学年', 2, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');

INSERT IGNORE INTO `department` (`dept_id`, `dept_name`, `dept_code`, `description`, `status`, `create_time`, `update_time`)
VALUES (1, '技术部', 'TECH', '负责技术学习、技术分享和创新实践', 1, NOW(), NOW()),
       (2, '综合部', 'GENERAL', '负责社团的日常运营与管理工作', 1, NOW(), NOW()),
       (3, '项目部', 'PUBLICITY', '负责资源整合、活动组织和内部协调', 1, NOW(), NOW()),
       (4, '媒体部', 'EXTERNAL', '负责社团宣传、内容创作和品牌建设', 1, NOW(), NOW());

INSERT IGNORE INTO `user` (`user_id`, `username`, `password`, `name`, `email`, `phone`, `major`, `github`, `dept_id`, `avatar`, `status`, `is_deleted`, `create_time`, `update_time`)
VALUES (1, 'admin', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', '管理员', 'admin@stu.ecnu.edu.cn', '13800000000', '计算机科学与技术', 'https://github.com/admin', 1, '/uploads/avatars/admin.jpg', 1, 0, '2025-08-15 14:57:57', NOW());

INSERT IGNORE INTO `role` (`role_id`, `role_name`, `role_code`, `description`, `status`, `create_time`, `update_time`)
VALUES (1, '超级管理员', 'SUPER_ADMIN', '拥有系统所有权限，可管理所有角色和用户', 1, NOW(), NOW()),
       (2, '管理员', 'ADMIN', '拥有大部分管理权限，可管理社员和审核简历', 1, NOW(), NOW()),
       (3, '社员', 'MEMBER', '社团正式成员，可查看简历和参与评审', 1, NOW(), NOW()),
       (4, '申请人', 'APPLICANT', '非社员，可提交简历申请加入社团', 1, NOW(), NOW());

INSERT IGNORE INTO `permission` (`permission_id`, `permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
VALUES (1, '管理管理员账号', 'admin:manage', NULL, '创建、编辑、删除管理员账号', NOW(), NOW()),
       (2, '管理社员账号', 'member:manage', NULL, '创建、编辑、删除社员账号', NOW(), NOW()),
       (3, '查看简历', 'resume:view', NULL, '查看所有简历信息', NOW(), NOW()),
       (4, '审核简历', 'resume:audit', NULL, '审核简历，决定通过或拒绝', NOW(), NOW()),
       (5, '管理招聘周期', 'cycle:manage', NULL, '创建、编辑、删除招聘周期', NOW(), NOW()),
       (6, '管理部门信息', 'dept:manage', NULL, '创建、编辑、删除部门信息', NOW(), NOW()),
       (7, '管理奖项信息', 'award:manage', NULL, '创建、编辑、删除奖项信息', NOW(), NOW()),
       (8, '分配角色', 'role:assign', NULL, '为用户分配或移除角色', NOW(), NOW()),
       (9, '管理权限', 'permission:manage', NULL, '管理角色权限分配', NOW(), NOW()),
       (10, '管理活动信息', 'activity:manage', NULL, '创建、编辑、删除活动信息', NOW(), NOW());

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`, `create_time`)
VALUES (1, 1, NOW());

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
VALUES (1, 1, NOW()), (1, 2, NOW()), (1, 3, NOW()), (1, 4, NOW()), (1, 5, NOW()),
       (1, 6, NOW()), (1, 7, NOW()), (1, 8, NOW()), (1, 9, NOW()), (1, 10, NOW()),
       (2, 2, NOW()), (2, 3, NOW()), (2, 4, NOW()), (2, 5, NOW()), (2, 6, NOW()), (2, 7, NOW()), (2, 10, NOW()),
       (3, 3, NOW());

SET FOREIGN_KEY_CHECKS = 1;
