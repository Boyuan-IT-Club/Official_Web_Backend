-- 按业务决定收回两处过宽的授权(对应 ADR-0003 的 Open Questions 1 与新增决定)。
--
-- 1) 部门增删改收归超管:dept:manage 从「管理员」角色移除。
-- 2) 社员不再能读全站简历:resume:view 从「社员」角色移除。
--    此前该权限是社员角色的唯一权限,解锁「查任意用户某周期的简历」「简历列表查询」
--    「全局搜索」三个接口 —— 等于全体社员可读所有申请人的简历与联系方式。
--    需要老社员帮看简历时,给那几个人加「面试官」角色,而不是给全体社员开权限。
--
-- 附带效果:社员角色移除后不再持有任何权限码,而管理端准入判定是
-- 「权限码数量 > 0」,因此社员将无法再登入管理后台 —— 这是期望行为。
--
-- 按 role_code / permission_code 关联删除,不写死 ID(V10/V13 撞号的教训)。
-- 幂等:重复执行时 DELETE 命中 0 行,无副作用。

DELETE rp FROM `role_permission` rp
    JOIN `role` r ON r.`role_id` = rp.`role_id`
    JOIN `permission` p ON p.`permission_id` = rp.`permission_id`
WHERE r.`role_code` = 'ADMIN'
  AND p.`permission_code` = 'dept:manage';

DELETE rp FROM `role_permission` rp
    JOIN `role` r ON r.`role_id` = rp.`role_id`
    JOIN `permission` p ON p.`permission_id` = rp.`permission_id`
WHERE r.`role_code` = 'MEMBER'
  AND p.`permission_code` = 'resume:view';
