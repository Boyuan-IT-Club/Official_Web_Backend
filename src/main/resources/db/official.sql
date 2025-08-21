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

 Date: 15/08/2025 17:32:19
*/

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for award_experience
-- ----------------------------
DROP TABLE IF EXISTS `award_experience`;
CREATE TABLE `award_experience`  (
  `award_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `award_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `award_time` date NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  PRIMARY KEY (`award_id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `award_experience_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of award_experience
-- ----------------------------
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (1, 2, 'ACM程序设计竞赛银奖', '2023-05-20', '在省级ACM程序设计竞赛中获得银奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (2, 2, '校级优秀学生奖学金', '2023-12-01', '因学业成绩优异获得校级一等奖学金');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (3, 2, '数学建模竞赛二等奖', '2023-09-15', '在全国大学生数学建模竞赛中获得二等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (4, 3, '大学生创新创业大赛一等奖', '2023-08-10', '带领团队在校级创新创业大赛中获得一等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (5, 3, '英语演讲比赛三等奖', '2023-11-25', '在外语学院举办的英语演讲比赛中获得三等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (6, 3, '优秀学生干部', '2023-12-01', '因担任学生会干部期间表现优秀获得此荣誉');

-- 添加更多测试用户的获奖经历
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (7, 4, '全国大学生数学竞赛一等奖', '2024-03-15', '在全国大学生数学竞赛中获得一等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (8, 4, '校级优秀学生干部', '2024-12-01', '担任学生会主席期间工作出色');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (9, 5, 'ACM国际大学生程序设计竞赛区域赛银奖', '2024-05-20', '在ACM国际大学生程序设计竞赛区域赛中获得银奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (10, 5, '蓝桥杯全国软件和信息技术专业人才大赛二等奖', '2024-04-10', '在蓝桥杯全国软件和信息技术专业人才大赛中获得二等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (11, 6, '全国大学生英语竞赛特等奖', '2024-05-15', '在全国大学生英语竞赛中获得特等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (12, 6, '校级社会实践优秀个人', '2024-12-01', '在暑期社会实践活动中表现突出');

-- ----------------------------
-- Table structure for resume
-- ----------------------------
DROP TABLE IF EXISTS `resume`;
CREATE TABLE `resume`  (
  `resume_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `cycle_id` int NOT NULL,
  `status` tinyint NULL DEFAULT 1,
  `submitted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`resume_id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `resume_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume
-- ----------------------------
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`) VALUES (1, 2, 2024, 3, '2025-08-15 14:03:27', '2025-08-15 13:58:51', '2025-08-15 14:05:36');
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`) VALUES (2, 4, 2025, 2, '2025-08-15 15:30:00', '2025-08-15 15:25:10', '2025-08-15 15:30:00');
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`) VALUES (3, 5, 2025, 1, '2025-08-15 16:45:00', '2025-08-15 16:40:20', '2025-08-15 16:45:00');
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`) VALUES (4, 6, 2025, 3, '2025-08-15 17:15:00', '2025-08-15 17:10:30', '2025-08-15 17:20:00');

-- ----------------------------
-- Table structure for resume_field_definition
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_definition`;
CREATE TABLE `resume_field_definition`  (
  `field_id` int NOT NULL AUTO_INCREMENT,
  `cycle_id` int NOT NULL,
  `field_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_label` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_required` tinyint(1) NULL DEFAULT 0,
  `sort_order` int NULL DEFAULT 0,
  `is_active` tinyint(1) NULL DEFAULT 1,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`field_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume_field_definition
-- ----------------------------
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (1, 2024, 'personal_statement', '个人陈述（必填）', 1, 1, 1, '2025-08-15 13:52:46', '2025-08-15 14:24:24');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (2, 2024, 'project_experience', '项目经历', 1, 2, 1, '2025-08-15 13:52:58', '2025-08-15 13:52:58');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (3, 2024, 'skills', '技能', 0, 3, 1, '2025-08-15 13:53:02', '2025-08-15 13:53:02');

-- 添加2025年份的简历字段定义
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (4, 2025, 'name', '姓名', 1, 1, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (5, 2025, 'major', '专业', 1, 2, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (6, 2025, 'email', '邮箱', 1, 3, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (7, 2025, 'phone', '手机号', 1, 4, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (8, 2025, 'grade', '年级', 1, 5, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (9, 2025, 'gender', '性别', 1, 6, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (10, 2025, 'expected_department', '期望部门', 1, 7, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (11, 2025, 'self_introduction', '自我介绍', 1, 8, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (12, 2025, 'tech_stack', '技术栈', 1, 9, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (13, 2025, 'project_experience', '项目经验', 1, 10, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (14, 2025, 'expected_interview_time', '期望的面试时间', 0, 11, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (15, 2025, 'personal_photo', '个人照片', 0, 12, 1, '2025-08-15 18:00:00', '2025-08-15 18:00:00');

-- ----------------------------
-- Table structure for resume_field_value
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_value`;
CREATE TABLE `resume_field_value`  (
  `value_id` int NOT NULL AUTO_INCREMENT,
  `resume_id` int NOT NULL,
  `field_id` int NOT NULL,
  `field_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`value_id`) USING BTREE,
  INDEX `resume_id`(`resume_id` ASC) USING BTREE,
  INDEX `field_id`(`field_id` ASC) USING BTREE,
  CONSTRAINT `resume_field_value_ibfk_1` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `resume_field_value_ibfk_2` FOREIGN KEY (`field_id`) REFERENCES `resume_field_definition` (`field_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume_field_value
-- ----------------------------
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (1, 1, 1, '我是一个热爱技术的学生，希望能在技术部门发挥自己的才能。', '2025-08-15 14:00:04', '2025-08-15 14:00:04');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (2, 1, 2, '参与开发了学校图书馆管理系统，使用Java和MySQL。', '2025-08-15 14:00:04', '2025-08-15 14:00:04');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (3, 1, 3, '熟悉Java、Python和前端技术。', '2025-08-15 14:00:04', '2025-08-15 14:00:04');

-- 添加2025年份的简历字段值
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (4, 2, 4, '测试用户1', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (5, 2, 5, '计算机科学与技术', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (6, 2, 6, 'test1@example.com', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (7, 2, 7, '13800000003', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (8, 2, 8, '大三', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (9, 2, 9, '男', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (10, 2, 10, '技术部', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (11, 2, 11, '热爱编程，有良好的团队合作精神', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (12, 2, 12, '["Java", "Spring Boot", "MySQL", "Redis"]', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (13, 2, 13, '参与开发学校图书馆管理系统，使用Java Spring Boot框架和MySQL数据库。负责后端接口开发和数据库设计。', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (34, 2, 14, '工作日下午', '2025-08-15 15:25:10', '2025-08-15 15:25:10');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (35, 2, 15, '/uploads/photos/user2.jpg', '2025-08-15 15:25:10', '2025-08-15 15:25:10');

INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (14, 3, 4, '测试用户2', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (15, 3, 5, '软件工程', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (16, 3, 6, 'test2@example.com', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (17, 3, 7, '13800000004', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (18, 3, 8, '大二', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (19, 3, 9, '女', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (20, 3, 10, '设计部', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (21, 3, 11, '对UI设计有浓厚兴趣，熟练使用各类设计软件', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (22, 3, 12, '["Photoshop", "Illustrator", "Figma", "HTML", "CSS"]', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (23, 3, 13, '设计学校官方网站界面，使用Figma进行UI设计，并使用HTML/CSS实现部分页面。', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (36, 3, 14, '周末全天', '2025-08-15 16:40:20', '2025-08-15 16:40:20');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (37, 3, 15, '/uploads/photos/user3.jpg', '2025-08-15 16:40:20', '2025-08-15 16:40:20');

INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (24, 4, 4, '测试用户3', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (25, 4, 5, '数据科学与大数据技术', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (26, 4, 6, 'test3@example.com', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (27, 4, 7, '13800000005', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (28, 4, 8, '大四', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (29, 4, 9, '男', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (30, 4, 10, '市场部', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (31, 4, 11, '具备良好的沟通能力和数据分析能力，希望能在市场推广方面发挥作用', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (32, 4, 12, '["Python", "数据分析", "市场分析", "Excel", "Tableau"]', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (33, 4, 13, '分析学校社团活动参与数据，使用Python进行数据清洗和分析，并用Tableau制作可视化报告。', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (38, 4, 14, '工作日晚上或周末', '2025-08-15 17:10:30', '2025-08-15 17:10:30');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (39, 4, 15, '/uploads/photos/user4.jpg', '2025-08-15 17:10:30', '2025-08-15 17:10:30');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `dept` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint(1) NULL DEFAULT 0,
  `is_member` tinyint(1) NULL DEFAULT 0,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (1, 'admin', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'ADMIN', '管理员', 'admin@boyuan.club', '13800000000', '技术部', '2025-08-15 13:13:58', 1, 1, NULL);
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (2, 'student1', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '学生1', '1234567890@stu.ecnu.edu.cn', '13800000001', '媒体部', '2025-08-15 13:13:58', 1, 1, '/uploads/avatars/107194c0-f53f-4aac-9671-fadd281f9669.png');
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (3, 'student2', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '学生2', '0987654321@stu.ecnu.edu.cn', '13800000002', '综合部', '2025-08-15 13:13:58', 1, 1, NULL);

-- 添加更多测试用户
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (4, 'testuser1', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '测试用户1', 'test1@example.com', '13800000003', '技术部', '2025-08-15 18:00:00', 1, 1, NULL);
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (5, 'testuser2', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '测试用户2', 'test2@example.com', '13800000004', '设计部', '2025-08-15 18:00:00', 1, 0, NULL);
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (6, 'testuser3', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '测试用户3', 'test3@example.com', '13800000005', '市场部', '2025-08-15 18:00:00', 1, 1, NULL);

SET FOREIGN_KEY_CHECKS = 1;