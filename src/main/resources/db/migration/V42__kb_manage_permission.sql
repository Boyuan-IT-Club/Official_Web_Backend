-- V42: 播种 kb:manage 权限码(RAG #134/R5,客服知识库管理面)。
--
-- 与 agent:monitor 分离(#121):知识库可单独授权给知识运营,
-- 不连带 agent:monitor 的运营/配置权限。风格同 V41(幂等,
-- 干净库与污染库都可安全执行):按 code 判重插入,角色绑定
-- LEFT JOIN 判重,重复执行无副作用。

-- 1) 补插权限行(按 code 判重)
INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '知识库管理', 'kb:manage', NULL, '管理客服知识库条目(列表/新建/编辑/启停/删除/重嵌)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'kb:manage');

-- 2) 补绑 role 1(超级管理员)、2(管理员)(按 code 关联,判重)
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'kb:manage'
LEFT JOIN `role_permission` rp
       ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_id` IN (1, 2) AND rp.`role_permission_id` IS NULL;
