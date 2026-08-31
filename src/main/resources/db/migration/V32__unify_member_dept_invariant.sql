-- 统一「社员 ⟺ 分配了部门」的不变式（存量对齐）。
--
-- 业务口径：不属于四个部门之一的就是非社员；是社员就必属四部门之一。
-- 此前「分配部门」「录取为社员」是两个互不联动的开关，存量里两个方向的
-- 违例都有（社员却无部门、有部门却非社员），dept 文本列与 dept_id 也有错位。
-- 服务层已把两个开关合一（分配部门即录取，取消分配/开除即两者同清），
-- 本迁移把存量对齐到同一口径：
--   1) dept 文本列有值但 dept_id 为空 → 按 department.dept_name 回填 dept_id；
--      dept 值不在部门表中的视为无效，两列同清
--   2) is_member := dept_id 是否非空
--   3) MEMBER 角色绑定与 is_member 对齐（V22 后该角色不带任何权限码，
--      增删绑定不影响任何人的实际权限）
-- 幂等：所有语句按当前状态收敛，重复执行无副作用。

-- 1) dept 文本列 → dept_id 回填
UPDATE `user` u
JOIN `department` d ON d.`dept_name` = u.`dept`
SET u.`dept_id` = d.`dept_id`
WHERE u.`is_deleted` = 0 AND u.`dept` IS NOT NULL AND u.`dept` != '' AND u.`dept_id` IS NULL;

-- dept 值不在部门表中：无效部门，两列同清
UPDATE `user` u
LEFT JOIN `department` d ON d.`dept_name` = u.`dept`
SET u.`dept` = NULL, u.`dept_id` = NULL
WHERE u.`is_deleted` = 0 AND u.`dept` IS NOT NULL AND u.`dept` != '' AND d.`dept_id` IS NULL;

-- 2) is_member 跟随部门
UPDATE `user` SET `is_member` = 1
WHERE `is_deleted` = 0 AND `dept_id` IS NOT NULL AND `is_member` = 0;

UPDATE `user` SET `is_member` = 0
WHERE `is_deleted` = 0 AND `dept_id` IS NULL AND `is_member` = 1;

-- 3) MEMBER 角色绑定对齐（按 role_code 关联，不写死 ID）
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`user_id`, r.`role_id`
FROM `user` u
JOIN `role` r ON r.`role_code` = 'MEMBER'
WHERE u.`is_deleted` = 0 AND u.`is_member` = 1
  AND NOT EXISTS (
    SELECT 1 FROM `user_role` ur
    WHERE ur.`user_id` = u.`user_id` AND ur.`role_id` = r.`role_id`
  );

DELETE ur FROM `user_role` ur
JOIN `role` r ON r.`role_id` = ur.`role_id` AND r.`role_code` = 'MEMBER'
JOIN `user` u ON u.`user_id` = ur.`user_id`
WHERE u.`is_deleted` = 0 AND u.`is_member` = 0;
