-- 评测管理权限:查看评测总览/详情/内部排行榜
INSERT IGNORE INTO `permission` (`permission_id`, `permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
VALUES (11, '查看评测', 'evaluation:view', NULL, '查看 Autograder 评测总览、详情与内部排行榜', NOW(), NOW());

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
VALUES (1, 11, NOW()),
       (2, 11, NOW());