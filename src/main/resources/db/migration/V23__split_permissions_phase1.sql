-- 权限拆分阶段一(见 ADR-0003):只加新码并绑定角色,不删旧码、不改准入。
-- 本迁移执行后任何账号的可达接口集合都不变 —— 注解同时接受新旧两个码。
--
-- 一律不写死 permission_id:V10/V13 都显式插入 id = 11 导致后者被 INSERT IGNORE
-- 静默跳过(见 V18 的事故记录)。这里按 permission_code 判存在性,id 交给自增,
-- 授权按 role_code/permission_code 关联并 LEFT JOIN 判重,可重复执行。
--
-- permission:manage 不在新增列表里:它在 V6 就已存在(此前 0 处引用),
-- 且已绑定超管,本阶段只是让注解开始真正使用它。

-- ---------------------------------------------------------------------------
-- 1) 新增 8 个权限码
-- ---------------------------------------------------------------------------
INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT t.n, t.c, NULL, t.d, NOW(), NOW()
FROM (
    SELECT '进入管理后台' AS n, 'console:access' AS c, '管理端准入门票,取代「持有任意权限码即可进入」的判定' AS d
    UNION ALL SELECT '管理用户', 'user:manage', '录取为社员、分配部门、冻结解冻、编辑与删除用户'
    UNION ALL SELECT '授予管理员', 'admin:grant', '授予或撤销管理员身份,仅超管'
    UNION ALL SELECT '系统运维', 'system:ops', '清理缓存等运维操作,仅超管'
    UNION ALL SELECT '面试排期', 'interview:schedule', '面试场次、时间窗、一键分配与导出、改期审批、预约总览'
    UNION ALL SELECT '录取结果', 'interview:result', '录取结果的读写与群发通知邮件'
    UNION ALL SELECT '评价表管理', 'interview:board:manage', '评价表开启与锁定、评分维度配置、加权总分总览'
    UNION ALL SELECT '飞书同步', 'feishu:sync', '飞书地点映射、推送面试安排、拉回录取表格'
) t
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.`permission_code` = t.c);

-- ---------------------------------------------------------------------------
-- 2) 授权:超管拿全部新码;管理员拿除 admin:grant / system:ops 外的;
--    面试官只拿 console:access(其评价权限由已有的 interview:evaluate 承担)。
--    社员与申请人不授予任何后台权限。
-- ---------------------------------------------------------------------------
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
         JOIN `permission` p
              ON (r.`role_code` = 'SUPER_ADMIN' AND p.`permission_code` IN (
                      'console:access', 'user:manage', 'admin:grant', 'system:ops',
                      'interview:schedule', 'interview:result', 'interview:board:manage', 'feishu:sync'))
                  OR (r.`role_code` = 'ADMIN' AND p.`permission_code` IN (
                      'console:access', 'user:manage',
                      'interview:schedule', 'interview:result', 'interview:board:manage', 'feishu:sync'))
                  OR (r.`role_code` = 'INTERVIEWER' AND p.`permission_code` IN (
                      'console:access'))
         LEFT JOIN `role_permission` rp
                   ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE rp.`role_id` IS NULL;
