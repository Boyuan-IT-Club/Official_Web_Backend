-- 问题反馈：仅支持提交和查看。
-- 权限按 permission_code 关联，避免依赖环境中的自增 ID。

CREATE TABLE IF NOT EXISTS `feedback` (
    `feedback_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`feedback_id`),
    KEY `idx_feedback_user_created` (`user_id`, `created_at`),
    KEY `idx_feedback_deleted_created` (`is_deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `permission`
    (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '查看问题反馈', 'feedback:view', NULL, '管理端查看全部用户问题反馈', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `permission` WHERE `permission_code` = 'feedback:view'
);

INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'feedback:view'
LEFT JOIN `role_permission` rp
       ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_code` IN ('ADMIN', 'SUPER_ADMIN')
  AND rp.`role_id` IS NULL;
