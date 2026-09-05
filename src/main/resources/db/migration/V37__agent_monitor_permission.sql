-- V37: 播种 agent:monitor 权限码(M6 #115,客服 Agent 管理面板)
--
-- 用途:管理面板三块(运营/配置/用量)统一权限码;Backend /api/admin/agent/**
-- 代理与 Agent 服务 /admin/** 都校验它(决策 #102)。
-- 策略同 V18(幂等,干净库与污染库都可安全执行):
--   1. 按 permission_code 存在性判断插入,不硬编码 id;
--   2. 角色绑定按 code→id 关联,LEFT JOIN 判重,重复执行无副作用。

-- 1) 补插权限行(按 code 判重)
INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '客服Agent运维', 'agent:monitor', NULL, '查看客服 Agent 对话/用量/日志并管理其热载配置', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'agent:monitor');

-- 2) 补绑 role 1(超级管理员)、2(管理员)(按 code 关联,判重)
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'agent:monitor'
LEFT JOIN `role_permission` rp
       ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_id` IN (1, 2) AND rp.`role_permission_id` IS NULL;
