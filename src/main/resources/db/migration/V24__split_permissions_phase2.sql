-- 权限拆分阶段二(见 ADR-0003):收回已被拆掉与已废弃的权限码绑定。
--
-- 只删三个码的授权,不删 permission 行本身(那是阶段三的清理):
--   admin:manage  —— 已拆成 user:manage / admin:grant / system:ops(V23 已授予)
--   member:manage —— 全后端 0 处引用,语义被 user:manage 覆盖
--   award:manage  —— 全后端 0 处引用,奖项走用户自助接口(仅需登录)
--
-- 刻意不动的两个:
--   resume:audit  —— 收窄为「改简历状态、删除简历」,仍是新模型里的正式权限
--   role:assign   —— 收窄为「给用户分配角色」,仍是新模型里的正式权限
--
-- 生效方式:JWT 的 permissionCodes 是登录时烙进令牌的,本迁移只影响此后新签发的令牌。
-- 已在线的用户在令牌过期前不受影响,而注解仍同时接受新旧码,因此不会出现中途失权。
--
-- 按 role_code/permission_code 关联删除,不写死 ID;重复执行命中 0 行。

DELETE rp FROM `role_permission` rp
    JOIN `permission` p ON p.`permission_id` = rp.`permission_id`
WHERE p.`permission_code` IN ('admin:manage', 'member:manage', 'award:manage');
