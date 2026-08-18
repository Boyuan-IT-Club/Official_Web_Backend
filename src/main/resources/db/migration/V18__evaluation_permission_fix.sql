-- V18: 修复 evaluation:view 权限种子在污染库上静默失败的问题
--
-- 背景(2026-08-17 线上事故):
--   V13 用硬编码 permission_id=11 + INSERT IGNORE 播种 evaluation:view;
--   但线上 permission_id=11 已被 interview:evaluate 占用 —— 该权限码不在任何迁移内,
--   系面试评价功能联调时在线上手工 INSERT(permission 表 V6 种子 1-10 后 AUTO_INCREMENT 恰为 11)。
--   INSERT IGNORE 遇到主键冲突静默跳过,V13 的权限行与角色绑定全部丢失,
--   导致超管/管理员(role 1/2)的 JWT 里没有 evaluation:view,前端 autograding 菜单被过滤。
--
-- 本迁移修复策略(幂等,干净库与污染库都可安全执行):
--   1. 按 permission_code 存在性判断插入,不硬编码 id(避开被占用的 11,用自增分配);
--   2. 角色绑定按 code→id 关联,LEFT JOIN 判重,重复执行无副作用。

-- 1) 补插权限行(按 code 判重)
INSERT INTO `permission` (`permission_name`, `permission_code`, `resource_identifier`, `description`, `create_time`, `update_time`)
SELECT '查看评测', 'evaluation:view', NULL, '查看 Autograder 评测总览、详情与内部排行榜', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'evaluation:view');

-- 2) 补绑 role 1(超级管理员)、2(管理员)(按 code 关联,判重)
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'evaluation:view'
LEFT JOIN `role_permission` rp
       ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_id` IN (1, 2)
  AND rp.`role_id` IS NULL;
