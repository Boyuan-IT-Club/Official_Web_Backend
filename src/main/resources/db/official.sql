/*
 Navicat Premium Data Transfer

 Source Server         : Myconnection
 Source Server Type    : MySQL
 Source Server Version : 80041 (8.0.41)
 Source Host           : localhost:3306
 Source Schema         : official

 Target Server Type    : MySQL
 Target Server Version : 80041 (8.0.41)
 File Encoding         : 65001

 Date: 24/08/2025 23:55:00
*/

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for recruitment_cycle
-- ----------------------------
DROP TABLE IF EXISTS `recruitment_cycle`;
CREATE TABLE `recruitment_cycle`
(
    `cycle_id`      int                                                           NOT NULL AUTO_INCREMENT COMMENT '招募活动ID',
    `cycle_name`    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '招募活动名称',
    `description`   text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '活动基本介绍',
    `start_date`    date                                                          NOT NULL COMMENT '活动开始日期',
    `end_date`      date                                                          NOT NULL COMMENT '活动截止日期',
    `academic_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '学年',
    `status`        tinyint                                                       NOT NULL DEFAULT '1' COMMENT '活动状态：1(未开始), 2(进行中), 3(已结束), 4(已关闭)',
    `is_active`     tinyint(1)                                                    NOT NULL DEFAULT '1' COMMENT '是否启用：0(禁用), 1(启用)',
    `created_at`    timestamp                                                     NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    timestamp                                                     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`cycle_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='招募活动表';

-- ----------------------------
-- Records of recruitment_cycle
-- ----------------------------
INSERT INTO `recruitment_cycle`
VALUES (1, '2024年秋季招新', '华东师范大学博远社团2024年秋季招新活动，欢迎各位同学加入我们的大家庭！', '2024-09-01',
        '2024-09-30', '2024-2025学年', 3, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00'),
       (2, '2025年秋季招新', '华东师范大学博远社团2025年秋季招新活动，寻找志同道合的你！', '2025-09-01', '2025-09-30',
        '2025-2026学年', 2, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');

-- ----------------------------
-- Table structure for award_experience
-- ----------------------------
DROP TABLE IF EXISTS `award_experience`;
CREATE TABLE `award_experience`
(
    `award_id`    int                                                           NOT NULL AUTO_INCREMENT,
    `user_id`     int                                                           NOT NULL,
    `award_name`  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `award_time`  date                                                          NOT NULL,
    `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL,
    `created_at`  timestamp                                                     NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  timestamp                                                     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`award_id`) USING BTREE,
    INDEX `user_id` (`user_id` ASC) USING BTREE,
    CONSTRAINT `award_experience_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of award_experience
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    `user_id`     int                                                           NOT NULL AUTO_INCREMENT,
    `username`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `password`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `role`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `name`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `email`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `phone`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `major`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '专业',
    `github`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'GitHub地址',
    `dept`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `create_time` timestamp                                                     NULL DEFAULT CURRENT_TIMESTAMP,
    `status`      tinyint(1)                                                    NULL DEFAULT 0,
    `is_member`   tinyint(1)                                                    NULL DEFAULT 0,
    `avatar`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    PRIMARY KEY (`user_id`) USING BTREE,
    UNIQUE INDEX `username` (`username` ASC) USING BTREE,
    UNIQUE INDEX `email` (`email` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user`
VALUES (1, 'admin', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'ADMIN', '管理员',
        'admin@stu.ecnu.edu.cn', '13800000000', '计算机科学与技术', 'https://github.com/admin', '技术部',
        '2025-08-15 14:57:57', 1, 1, '/uploads/avatars/admin.jpg');
INSERT INTO `user`
VALUES (2, 'dinghuaye', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'ADMIN', '丁华烨',
        '10245101480@stu.ecnu.edu.cn', '15736888997', '软件工程', '', '综合部',
        '2025-08-15 14:57:57', 1, 1, '/uploads/avatars/dinghuaye.jpg');
INSERT INTO `user`
VALUES (3, 'hucongyu', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'ADMIN', '胡淙煜',
        '10245101417@stu.ecnu.edu.cn', '13136397281', '软件工程', '', '技术部',
        '2025-08-15 14:57:57', 1, 1, '/uploads/avatars/hucongyu.jpg');

-- ----------------------------
-- Table structure for resume
-- ----------------------------
DROP TABLE IF EXISTS `resume`;
CREATE TABLE `resume`
(
    `resume_id`    int       NOT NULL AUTO_INCREMENT,
    `user_id`      int       NOT NULL,
    `cycle_id`     int       NOT NULL,
    `status`       tinyint   NOT NULL DEFAULT 1 COMMENT '简历状态: 1(草稿), 2(已提交), 3(评审中), 4(通过), 5(未通过)',
    `submitted_at` timestamp NULL     DEFAULT NULL COMMENT '提交时间',
    `created_at`   timestamp NULL     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   timestamp NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`resume_id`) USING BTREE,
    UNIQUE INDEX `uk_user_cycle` (`user_id`, `cycle_id`) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_cycle_id` (`cycle_id` ASC) USING BTREE,
    CONSTRAINT `resume_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `resume_ibfk_2` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume
-- ----------------------------

-- ----------------------------
-- Table structure for resume_field_definition
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_definition`;
CREATE TABLE `resume_field_definition`
(
    `field_id`    int                                                           NOT NULL AUTO_INCREMENT,
    `cycle_id`    int                                                           NOT NULL,
    `field_key`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL,
    `field_label` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `is_required` tinyint(1)                                                    NULL DEFAULT 0,
    `sort_order`  int                                                           NULL DEFAULT 0,
    `is_active`   tinyint(1)                                                    NULL DEFAULT 1,
    `created_at`  timestamp                                                     NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  timestamp                                                     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`field_id`) USING BTREE,
    INDEX `idx_cycle_id` (`cycle_id` ASC) USING BTREE,
    CONSTRAINT `resume_field_definition_ibfk_1` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume_field_definition
-- ----------------------------
-- 删除2024年的简历字段定义

-- 2025年份的简历字段定义（更新后的版本）
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (4, 2, 'name', '姓名', 1, 1, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (5, 2, 'major', '专业', 1, 2, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (6, 2, 'email', '邮箱', 1, 3, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (7, 2, 'phone', '手机号', 1, 4, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (8, 2, 'grade', '年级', 1, 5, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (9, 2, 'gender', '性别', 1, 6, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (10, 2, 'expected_departments', '期望部门', 1, 7, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (11, 2, 'self_introduction', '自我介绍', 1, 8, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (12, 2, 'tech_stack', '技术栈', 1, 9, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (13, 2, 'project_experience', '项目经验', 1, 10, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (14, 2, 'expected_interview_time', '期望的面试时间', 0, 11, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (15, 2, 'personal_photo', '个人照片', 0, 12, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (16, 2, 'student_id', '学号', 1, 0, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (17, 2, 'introduction', '个人简介', 1, 13, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (18, 2, 'reason', '加入理由', 1, 14, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (19, 2, 'github', 'GitHub地址', 0, 15, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');

-- ----------------------------
-- Table structure for resume_field_value
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_value`;
CREATE TABLE `resume_field_value`
(
    `value_id`    int                                                   NOT NULL AUTO_INCREMENT,
    `resume_id`   int                                                   NOT NULL,
    `field_id`    int                                                   NOT NULL,
    `field_value` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    `created_at`  timestamp                                             NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  timestamp                                             NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`value_id`) USING BTREE,
    INDEX `resume_id` (`resume_id` ASC) USING BTREE,
    INDEX `field_id` (`field_id` ASC) USING BTREE,
    CONSTRAINT `resume_field_value_ibfk_1` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `resume_field_value_ibfk_2` FOREIGN KEY (`field_id`) REFERENCES `resume_field_definition` (`field_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume_field_value
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;