-- 研发工作台成员资格以「官网研发组成员」角色绑定为准。
-- 只注册角色、权限和版本字段，不改任何现有用户的角色绑定。
-- 重复执行这些插入不会重复授权。user_role 已有 uk_user_role(user_id, role_id)。

ALTER TABLE `user`
  ADD COLUMN `workbench_membership_version` BIGINT NOT NULL DEFAULT 0
    COMMENT '此账号的研发组绑定每次真正加入或退出时加一' AFTER `is_deleted`;

INSERT INTO `role` (`role_name`, `role_code`, `description`, `status`, `create_time`, `update_time`)
SELECT '官网研发组成员', 'website_dev_member', '绑定后可进入研发工作台。成员资格只看这一条角色绑定。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `role_code` = 'website_dev_member');

INSERT INTO `permission` (`permission_name`, `permission_code`, `description`, `create_time`, `update_time`)
SELECT '进入研发工作台', 'workbench:access', '有效研发组成员进入工作台', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'workbench:access');

INSERT INTO `permission` (`permission_name`, `permission_code`, `description`, `create_time`, `update_time`)
SELECT '管理研发组成员', 'workbench:member:manage', '在工作台搜索并增删研发组成员。普通研发成员不自动获得。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `permission_code` = 'workbench:member:manage');

INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'workbench:access'
LEFT JOIN `role_permission` rp ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_code` = 'website_dev_member' AND rp.`role_permission_id` IS NULL;

-- 管理权交给现有管理角色，不授给普通研发成员。官网管理员仍可在角色后台调整。
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
SELECT r.`role_id`, p.`permission_id`, NOW()
FROM `role` r
JOIN `permission` p ON p.`permission_code` = 'workbench:member:manage'
LEFT JOIN `role_permission` rp ON rp.`role_id` = r.`role_id` AND rp.`permission_id` = p.`permission_id`
WHERE r.`role_code` IN ('SUPER_ADMIN', 'ADMIN') AND rp.`role_permission_id` IS NULL;
