-- V46: 播种 evaluation:run 权限码(初筛执行权,resume-eval 评审闸门)。
--
-- 用途:把「触发 AI 初筛 / 写简历状态 6(AI初筛中)」从 resume:audit 里拆出来，
-- 单独成为一个权限码，仅授予超管(role 1)与管理员(role 2)。
-- 动机(评审):
--   1. 状态 6 是瞬态系统状态，普通审核员不应能手工写(误设会让简历永久卡在"初筛中");
--   2. resume:audit 保留 1/2/4/5 的审核权，6 单独由本权限码放行;
--   3. Agent 初筛执行走本权限码，超管/管理员持有，面试官不持有。
-- 策略同 V41/V42(幂等,干净库与污染库都可安全执行):
--   1. 按 permission_code 存在性判断插入,不硬编码 id;
--   2. 角色绑定按 code→id 关联,LEFT JOIN 判重,重复执行无副作用。

-- 1) 补插权限行(按 code 判重)
INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '初筛执行', 'evaluation:run', NULL, '触发 AI 初筛并设置简历状态 6(AI初筛中)；仅超管与管理员', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'evaluation:run');

-- 2) 补绑 role 1(超级管理员)、2(管理员)(按 code 关联,判重)
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'evaluation:run'
LEFT JOIN `role_permission` rp
       ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_id` IN (1, 2) AND rp.`role_permission_id` IS NULL;
