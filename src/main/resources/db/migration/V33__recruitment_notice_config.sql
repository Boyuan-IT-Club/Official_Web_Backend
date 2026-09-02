-- 招新通知所需的配置：候场教室、负责人联系方式、各类二维码。
--
-- 为什么放在周期上而不是全局：候场教室、答疑群、负责人每一届都会换，
-- 挂全局就得每年手动改一次，且历史周期的邮件内容无从追溯。
--
-- 幂等守卫沿用 V14/V20/V25/V26/V27 的写法。

-- ── 候场教室：同一周期通常只有一间，直接挂在周期上 ──
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruitment_cycle'
       AND COLUMN_NAME = 'waiting_room') = 0,
    'ALTER TABLE `recruitment_cycle` ADD COLUMN `waiting_room` varchar(100) NULL COMMENT ''候场教室,面试提醒邮件里用''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ── 负责人联系方式：未录取通知邮件末尾附上 ──
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruitment_cycle'
       AND COLUMN_NAME = 'contact_info') = 0,
    'ALTER TABLE `recruitment_cycle` ADD COLUMN `contact_info` varchar(500) NULL COMMENT ''本届负责人联系方式,未录取通知邮件里用''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ── 二维码 ──
-- 一张表放三类码，靠 qr_type 区分：
--   DEPT       部门群（每个部门一张，录取通知里按录取部门取对应那张）
--   MAIN_GROUP 社团大群（所有录取者都附）
--   QA_GROUP   招新答疑群（简历填写页展示）
--
-- dept_id 用 0 而不是 NULL 表示「与部门无关」：MySQL 的唯一索引允许多个 NULL，
-- 用 NULL 的话 MAIN_GROUP 能被重复插入多条。
CREATE TABLE IF NOT EXISTS `recruitment_qr_code`
(
    `id`         int          NOT NULL AUTO_INCREMENT,
    `cycle_id`   int          NOT NULL COMMENT '招募周期ID',
    `qr_type`    varchar(20)  NOT NULL COMMENT 'DEPT / MAIN_GROUP / QA_GROUP',
    `dept_id`    int          NOT NULL DEFAULT 0 COMMENT '部门ID；非部门类型固定为 0',
    `image_url`  varchar(500) NOT NULL COMMENT '二维码图片地址（COS 对象键或完整 URL）',
    `remark`     varchar(200) NULL COMMENT '备注，如群名',
    `created_at` timestamp    NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cycle_type_dept` (`cycle_id`, `qr_type`, `dept_id`),
    CONSTRAINT `fk_qr_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='招新相关二维码：部门群 / 大群 / 答疑群';
