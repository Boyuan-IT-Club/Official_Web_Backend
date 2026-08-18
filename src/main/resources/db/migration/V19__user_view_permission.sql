-- 目的一:新增只读用户查看权限 user:view,让「管理员(ADMIN)」角色能打开管理端
--         「用户与角色」页但不能改动。此前 GET /api/admin/users 与所有写接口
--         同用 admin:manage,而 ADMIN 角色没有该权限(V6 只给了超管),
--         结果管理员一进这个页面就 403。
--
-- 目的二:修 V10/V13 的 permission_id 撞号。两者都显式插入了 permission_id = 11
--         (V10 = interview:evaluate,V13 = evaluation:view),且都是 INSERT IGNORE。
--         V10 版本号更小先执行占住主键,V13 那条被静默忽略 —— 于是
--         evaluation:view 在任何按 Flyway 建的库上都不存在,而
--         EvaluationAdminController 每个接口都 @PreAuthorize 它,
--         整个评测管理模块对所有角色 403。
--
-- 因此本迁移一律不写死 permission_id:交给 AUTO_INCREMENT,靠 uk_permission_code
-- 保证幂等,授权也按 permission_code 子查询定位。显式 ID 是上面那个 bug 的根源,
-- 后续新增权限请沿用这里的写法,不要再抄 V6 的显式 ID 风格。

INSERT IGNORE INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
VALUES ('查看用户信息', 'user:view', NULL, '只读查看用户列表与详情,不含任何修改操作', NOW(), NOW()),
       ('查看评测',     'evaluation:view', NULL, '查看 Autograder 评测总览、详情与内部排行榜', NOW(), NOW());

-- user:view 给超管与管理员;evaluation:view 补上 V13 本该完成的授权(超管 + 管理员)
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
         JOIN `permission` p
              ON (p.`permission_code` = 'user:view'       AND r.`role_code` IN ('SUPER_ADMIN', 'ADMIN'))
                  OR (p.`permission_code` = 'evaluation:view' AND r.`role_code` IN ('SUPER_ADMIN', 'ADMIN'));
