-- 初筛执行权：把「触发 AI 初筛 / 写简历状态 6(AI初筛中)」从 resume:audit 拆出来，
-- 单独成为一个权限码，仅授予超管(role 1)与管理员(role 2)。
--
-- 动机：状态 6 是瞬态系统状态，普通审核员不应能手工写——误设会让简历永久卡在
-- 「初筛中」。resume:audit 保留 1/2/4/5 的审核权；Agent 初筛执行持本权限码。
--
-- 幂等：按 permission_code 判存在性插入（不硬编码 id），角色绑定按 code 关联、
-- LEFT JOIN 判重，重复执行无副作用。同 V41/V42 的写法。
INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '初筛执行', 'evaluation:run', NULL, '触发 AI 初筛并设置简历状态 6(AI初筛中)；仅超管与管理员', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'evaluation:run');

INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'evaluation:run'
LEFT JOIN `role_permission` rp
       ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_id` IN (1, 2) AND rp.`role_permission_id` IS NULL;
