-- 新增只读用户查看权限 user:view,让「管理员(ADMIN)」角色能打开管理端
-- 「用户与角色」页但不能改动。
--
-- 此前 GET /api/admin/users 与该控制器的 9 个写接口同用 admin:manage,
-- 而 V6 只把 admin:manage 给了超管(role 1),ADMIN(role 2)一进这个页面就 403。
-- 放宽后 GET 接受 hasAnyAuthority('admin:manage','user:view'),写接口一律不动。
--
-- 不硬编码 permission_id:V10/V13 都显式插入 id = 11 导致 V13 被 INSERT IGNORE
-- 静默跳过(见 V18 的事故记录)。这里按 permission_code 判存在性,id 交给自增,
-- 角色绑定按 code 关联并 LEFT JOIN 判重,干净库与已有库都可重复执行。

INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '查看用户信息', 'user:view', NULL, '只读查看用户列表与详情,不含任何修改操作', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'user:view');

INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
         JOIN `permission` p ON p.`permission_code` = 'user:view'
         LEFT JOIN `role_permission` rp
                   ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_code` IN ('SUPER_ADMIN', 'ADMIN')
  AND rp.`role_id` IS NULL;
