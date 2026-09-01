-- 存量社员回填 MEMBER 角色绑定。
--
-- 背景:管理端「录取为社员」历史上只更新 user.is_member 列;#162(2026-08-19)起
-- 才在录取/开除时同步 user_role 里的 MEMBER 角色(syncMemberRole),但没有回填存量——
-- 于是老社员 is_member=1 却没有任何角色绑定。用户列表按 ADR-0001 以 RBAC 角色为
-- 唯一真源筛选:「社员」= 持有 MEMBER 角色 → 存量社员一个都筛不出来;
-- 「非社员」= APPLICANT 或无任何角色 → 把这批无角色的老社员全捞进来,
-- 角色列清一色「暂无角色」。这就是管理端「筛社员/非社员看不到角色」的根因。
--
-- 只做正向回填(is_member=1 补角色),不反向清理「is_member=0 却有 MEMBER 角色」——
-- 那可能是管理员在角色管理里手工授予的,迁移不该替人做删角色的决定。
-- 按 role_code 关联而不写死 role_id(V10/V13 撞号的教训)。
-- 幂等:NOT EXISTS 守卫,重复执行命中 0 行。

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`user_id`, r.`role_id`
FROM `user` u
JOIN `role` r ON r.`role_code` = 'MEMBER'
WHERE u.`is_deleted` = 0
  AND u.`is_member` = 1
  AND NOT EXISTS (
    SELECT 1 FROM `user_role` ur
    WHERE ur.`user_id` = u.`user_id` AND ur.`role_id` = r.`role_id`
  );
