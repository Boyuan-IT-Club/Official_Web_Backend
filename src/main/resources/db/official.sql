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

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for award_experience
-- ----------------------------
DROP TABLE IF EXISTS `award_experience`;
CREATE TABLE `award_experience`  (
  `award_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `award_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `award_time` timestamp NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  PRIMARY KEY (`award_id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `award_experience_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of award_experience
-- ----------------------------
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (1, 2, 'ACM程序设计竞赛银奖', '2023-05-20 00:00:00', '在省级ACM程序设计竞赛中获得银奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (2, 2, '校级优秀学生奖学金', '2023-12-01 00:00:00', '因学业成绩优异获得校级一等奖学金');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (3, 2, '数学建模竞赛二等奖', '2023-09-15 00:00:00', '在全国大学生数学建模竞赛中获得二等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (4, 3, '大学生创新创业大赛一等奖', '2023-08-10 00:00:00', '带领团队在校级创新创业大赛中获得一等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (5, 3, '英语演讲比赛三等奖', '2023-11-25 00:00:00', '在外语学院举办的英语演讲比赛中获得三等奖');
INSERT INTO `award_experience` (`award_id`, `user_id`, `award_name`, `award_time`, `description`) VALUES (6, 3, '优秀学生干部', '2023-12-01 00:00:00', '因担任学生会干部期间表现优秀获得此荣誉');

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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume
-- ----------------------------
INSERT INTO `resume` (`resume_id`, `user_id`, `cycle_id`, `status`, `submitted_at`, `created_at`, `updated_at`) VALUES (1, 2, 2024, 3, '2025-08-15 14:03:27', '2025-08-15 13:58:51', '2025-08-15 14:05:36');

-- ----------------------------
-- Table structure for resume_field_definition
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_definition`;
CREATE TABLE `resume_field_definition`  (
  `field_id` int NOT NULL AUTO_INCREMENT,
  `cycle_id` int NOT NULL,
  `field_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `field_label` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `is_required` tinyint(1) NULL DEFAULT 0,
  `sort_order` int NULL DEFAULT 0,
  `is_active` tinyint(1) NULL DEFAULT 1,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`field_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume_field_definition
-- ----------------------------
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (1, 2024, 'personal_statement', '个人陈述（必填）', 1, 1, 1, '2025-08-15 13:52:46', '2025-08-15 14:24:24');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (2, 2024, 'project_experience', '项目经历', 1, 2, 1, '2025-08-15 13:52:58', '2025-08-15 13:52:58');
INSERT INTO `resume_field_definition` (`field_id`, `cycle_id`, `field_key`, `field_label`, `is_required`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (3, 2024, 'skills', '技能', 0, 3, 1, '2025-08-15 13:53:02', '2025-08-15 13:53:02');

-- ----------------------------
-- Table structure for resume_field_value
-- ----------------------------
DROP TABLE IF EXISTS `resume_field_value`;
CREATE TABLE `resume_field_value`  (
  `value_id` int NOT NULL AUTO_INCREMENT,
  `resume_id` int NOT NULL,
  `field_id` int NOT NULL,
  `field_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`value_id`) USING BTREE,
  INDEX `resume_id`(`resume_id` ASC) USING BTREE,
  INDEX `field_id`(`field_id` ASC) USING BTREE,
  CONSTRAINT `resume_field_value_ibfk_1` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`resume_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `resume_field_value_ibfk_2` FOREIGN KEY (`field_id`) REFERENCES `resume_field_definition` (`field_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resume_field_value
-- ----------------------------
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (1, 1, 1, '我是一个热爱技术的学生，希望能在技术部门发挥自己的才能。', '2025-08-15 14:00:04', '2025-08-15 14:00:04');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (2, 1, 2, '参与开发了学校图书馆管理系统，使用Java和MySQL。', '2025-08-15 14:00:04', '2025-08-15 14:00:04');
INSERT INTO `resume_field_value` (`value_id`, `resume_id`, `field_id`, `field_value`, `created_at`, `updated_at`) VALUES (3, 1, 3, '熟悉Java、Python和前端技术。', '2025-08-15 14:00:04', '2025-08-15 14:00:04');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `dept` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint(1) NULL DEFAULT 0,
  `is_member` tinyint(1) NULL DEFAULT 0,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (1, 'admin', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'ADMIN', '管理员', 'admin@boyuan.club', '13800000000', '技术部', '2025-08-15 13:13:58', 1, 1, NULL);
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (2, 'student1', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '学生1', '1234567890@stu.ecnu.edu.cn', '13800000001', '媒体部', '2025-08-15 13:13:58', 1, 1, '/uploads/avatars/107194c0-f53f-4aac-9671-fadd281f9669.png');
INSERT INTO `user` (`user_id`, `username`, `password`, `role`, `name`, `email`, `phone`, `dept`, `create_time`, `status`, `is_member`, `avatar`) VALUES (3, 'student2', '$2a$10$UO3ZwFXZIkGeG8nqQZRR7.iHL7QtapfHoOuqHc8CRhPxMwxKuJbry', 'USER', '学生2', '0987654321@stu.ecnu.edu.cn', '13800000002', '综合部', '2025-08-15 13:13:58', 1, 1, NULL);

SET FOREIGN_KEY_CHECKS = 1;