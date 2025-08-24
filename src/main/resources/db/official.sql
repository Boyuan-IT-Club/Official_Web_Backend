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
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (1, 2, 'ACM国际大学生程序设计竞赛区域赛银奖', '2024-11-15',
        '在ACM国际大学生程序设计竞赛区域赛中获得银奖，展现了优秀的编程和团队协作能力');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (2, 3, '全国大学生数学建模竞赛二等奖', '2024-09-20',
        '在全国大学生数学建模竞赛中获得二等奖，体现了扎实的数学基础和建模能力');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (3, 4, '校级优秀学生干部', '2024-12-01', '因在学生组织中的出色表现获得校级优秀学生干部称号');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (4, 5, '全国大学生英语竞赛三等奖', '2024-05-10', '在全国大学生英语竞赛中获得三等奖，展现了良好的英语水平');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (5, 6, '校级三好学生', '2024-12-01', '因学业和综合素质优秀获得校级三好学生称号');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (6, 7, 'UI设计大赛一等奖', '2024-06-15', '在校级UI设计大赛中获得一等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`)
VALUES (7, 8, '市场营销策划大赛三等奖', '2024-05-10', '在省级市场营销策划大赛中获得三等奖');

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
VALUES (2, 'testuser1', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '测试用户1',
        'testuser1@stu.ecnu.edu.cn', '13800000001', '软件工程', 'https://github.com/testuser1', '技术部',
        '2025-08-15 14:57:57', 1, 0, '/uploads/avatars/user1.jpg');
INSERT INTO `user`
VALUES (3, 'testuser2', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '测试用户2',
        'testuser2@stu.ecnu.edu.cn', '13800000002', '数据科学与大数据技术', 'https://github.com/testuser2', '设计部',
        '2025-08-15 14:57:57', 1, 0, '/uploads/avatars/user2.jpg');
INSERT INTO `user`
VALUES (4, 'student1', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '张三',
        'student1@stu.ecnu.edu.cn', '13800000003', '计算机科学与技术', 'https://github.com/zhangsan', '技术部',
        '2025-08-15 15:20:00', 1, 0, '/uploads/avatars/student1.jpg');
INSERT INTO `user`
VALUES (5, 'student2', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '李四',
        'student2@stu.ecnu.edu.cn', '13800000004', '数字媒体技术', 'https://github.com/lisi', '设计部',
        '2025-08-15 16:35:10', 1, 0, '/uploads/avatars/student2.jpg');
INSERT INTO `user`
VALUES (6, 'student3', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '王五',
        'student3@stu.ecnu.edu.cn', '13800000005', '市场营销', 'https://github.com/wangwu', '市场部',
        '2025-08-15 17:05:20', 1, 0, '/uploads/avatars/student3.jpg');
INSERT INTO `user`
VALUES (7, 'student4', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '赵六',
        'student4@stu.ecnu.edu.cn', '13800000006', '软件工程', 'https://github.com/zhaoliu', '技术部',
        '2025-08-15 17:30:30', 1, 0, '/uploads/avatars/student4.jpg');
INSERT INTO `user`
VALUES (8, 'student5', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '钱七',
        'student5@stu.ecnu.edu.cn', '13800000007', '视觉传达设计', 'https://github.com/qianqi', '设计部',
        '2025-08-15 17:45:40', 1, 0, '/uploads/avatars/student5.jpg');
INSERT INTO `user`
VALUES (9, 'student6', '$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC', 'USER', '孙八',
        'student6@stu.ecnu.edu.cn', '13800000008', '数据科学与大数据技术', 'https://github.com/sunba', '技术部',
        '2025-08-15 18:00:50', 1, 0, '/uploads/avatars/student6.jpg');

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
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`)
VALUES (2, 4, 2, 2, '2025-08-15 15:30:00', '2025-08-15 15:25:10', '2025-08-15 15:30:00');
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`)
VALUES (3, 5, 2, 1, '2025-08-15 16:45:00', '2025-08-15 16:40:20', '2025-08-15 16:45:00');
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`)
VALUES (4, 6, 2, 3, '2025-08-15 17:15:00', '2025-08-15 17:10:30', '2025-08-15 17:20:00');

-- 为更多用户添加简历
INSERT INTO `resume` (`user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`)
VALUES (7, 2, 1, NULL, '2025-08-15 17:35:00', '2025-08-15 17:35:00'),
       (8, 2, 2, '2025-08-15 17:50:00', '2025-08-15 17:45:00', '2025-08-15 17:50:00'),
       (9, 2, 4, '2025-08-15 18:05:00', '2025-08-15 18:00:00', '2025-08-15 18:10:00');

-- ----------------------------
-- Table structure for resume_field_definition
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_definition`;
CREATE TABLE `resume_field_definition`
(
    `field_id`    int                                                           NOT NULL AUTO_INCREMENT,
    `cycle_id`    int                                                           NOT NULL,
    `field_key`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL,
    `field_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
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
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`,
                                       `is_active`, `created_at`, `updated_at`)
VALUES (20, 2, 'github_url', 'GitHub地址', 0, 16, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');

-- ----------------------------
-- Table structure for resume_field_value
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_value`;
CREATE TABLE `resume_field_value`
(
    `value_id`    int                                                   NOT NULL AUTO_INCREMENT,
    `resume_id`   int                                                   NOT NULL,
    `field_id`    int                                                   NOT NULL,
    `field_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
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
-- 删除2024年的简历字段值
-- 保留并更新2025年的简历字段值

INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 4, '测试用户1', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 5, '计算机科学与技术', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 6, 'testuser1@stu.ecnu.edu.cn', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 7, '13800000003', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 8, '大三', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 9, '男', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 10, '["技术部"]', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 11, '热爱编程，有良好的团队合作精神', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 12, '["Java", "Spring Boot", "MySQL", "Redis"]', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 13, '参与开发学校图书馆管理系统，使用Java Spring Boot框架和MySQL数据库。负责后端接口开发和数据库设计。',
        '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 14, '工作日下午', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 15, '/uploads/photos/user2.jpg', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 16, '202312345678901', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 17, '我是一个对技术充满热情的学生，希望能在技术部门学习和成长', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 18, '希望提升自己的技术能力，同时为社团发展贡献力量', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 19, 'https://github.com/testuser1', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (2, 20, 'https://github.com/testuser1', '2025-08-15 15:25:10', '2025-08-15 15:25:10');

INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 4, '测试用户2', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 5, '软件工程', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 6, 'testuser2@stu.ecnu.edu.cn', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 7, '13800000004', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 8, '大二', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 9, '女', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 10, '["设计部"]', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 11, '对UI设计有浓厚兴趣，熟练使用各类设计软件', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 12, '["Photoshop", "Illustrator", "Figma", "HTML", "CSS"]', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 13, '设计学校官方网站界面，使用Figma进行UI设计，并使用HTML/CSS实现部分页面。', '2025-08-15 16:40:20',
        '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 14, '周末全天', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 15, '/uploads/photos/user3.jpg', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 16, '202212345678902', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 17, '我是一个创意丰富的设计师，希望能在设计部发挥自己的才能', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 18, '希望通过参与社团活动提升设计能力，结识志同道合的朋友', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 19, 'https://github.com/testuser2', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (3, 20, 'https://github.com/testuser2', '2025-08-15 16:40:20', '2025-08-15 16:40:20');

INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 4, '测试用户3', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 5, '数据科学与大数据技术', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 6, 'testuser3@stu.ecnu.edu.cn', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 7, '13800000005', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 8, '大四', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 9, '男', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 10, '["市场部"]', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 11, '具备良好的沟通能力和数据分析能力，希望能在市场推广方面发挥作用', '2025-08-15 17:10:30',
        '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 12, '["Python", "数据分析", "市场分析", "Excel", "Tableau"]', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 13, '分析学校社团活动参与数据，使用Python进行数据清洗和分析，并用Tableau制作可视化报告。',
        '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 14, '工作日晚上或周末', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 15, '/uploads/photos/user4.jpg', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 16, '202112345678903', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 17, '我是一个善于沟通和策划的学生，希望能在市场部发挥作用', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 18, '希望通过参与社团活动提升市场运营能力，为社团发展贡献力量', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 19, 'https://github.com/testuser3', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`)
VALUES (4, 20, 'https://github.com/testuser3', '2025-08-15 17:10:30', '2025-08-15 17:10:30');

-- 添加更多简历字段值
INSERT INTO `resume_field_value` (`resume_id`, `field_id`, `field_value`)
VALUES (5, 4, '张三'),
       (5, 5, '计算机科学与技术'),
       (5, 6, 'student3@stu.ecnu.edu.cn'),
       (5, 7, '13800000006'),
       (5, 8, '大二'),
       (5, 9, '男'),
       (5, 10, '["技术部","设计部"]'),
       (5, 11, '热爱编程和开源技术，有良好的团队协作能力'),
       (5, 12, '["Java", "Python", "Linux", "Docker"]'),
       (5, 13, '参与学校开源社区项目，贡献代码并维护文档'),
       (5, 14, '2025-09-15 14:00:00'),
       (5, 15, '/uploads/photos/student3.jpg'),
       (5, 16, '202312345678901'),
       (5, 17, '我是一个对技术充满热情的学生，希望能在技术部门学习和成长'),
       (5, 18, '希望提升自己的技术能力，同时为社团发展贡献力量'),
       (5, 19, 'https://github.com/zhangsan'),
       (5, 20, 'https://github.com/zhangsan'),

       (6, 4, '李四'),
       (6, 5, '数字媒体技术'),
       (6, 6, 'student4@stu.ecnu.edu.cn'),
       (6, 7, '13800000007'),
       (6, 8, '大三'),
       (6, 9, '女'),
       (6, 10, '["设计部","市场部"]'),
       (6, 11, '热爱设计和艺术，有独特的审美和创意能力'),
       (6, 12, '["Photoshop", "Illustrator", "Figma", "After Effects"]'),
       (6, 13, '设计学校宣传海报和活动物料，参与多个设计项目'),
       (6, 14, '2025-09-16 10:00:00'),
       (6, 15, '/uploads/photos/student4.jpg'),
       (6, 16, '202212345678902'),
       (6, 17, '我是一个创意丰富的设计师，希望能在设计部发挥自己的才能'),
       (6, 18, '希望通过参与社团活动提升设计能力，结识志同道合的朋友'),
       (6, 19, 'https://github.com/lisi'),
       (6, 20, 'https://github.com/lisi'),

       (7, 4, '王五'),
       (7, 5, '市场营销'),
       (7, 6, 'student5@stu.ecnu.edu.cn'),
       (7, 7, '13800000008'),
       (7, 8, '大四'),
       (7, 9, '男'),
       (7, 10, '["市场部","技术部"]'),
       (7, 11, '具备良好的沟通能力和市场洞察力，善于团队协作'),
       (7, 12, '["市场分析", "数据处理", "活动策划", "演讲"]'),
       (7, 13, '策划并执行学校多项营销活动，取得良好效果'),
       (7, 14, '2025-09-17 15:00:00'),
       (7, 15, '/uploads/photos/student5.jpg'),
       (7, 16, '202112345678903'),
       (7, 17, '我是一个善于沟通和策划的学生，希望能在市场部发挥作用'),
       (7, 18, '希望通过参与社团活动提升市场运营能力，为社团发展贡献力量'),
       (7, 19, 'https://github.com/wangwu'),
       (7, 20, 'https://github.com/wangwu');

SET FOREIGN_KEY_CHECKS = 1;