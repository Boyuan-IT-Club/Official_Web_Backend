-- user.dept 与 user.is_member 列对齐(同 V14 处理 user.role 的原因)。
--
-- 这两列只存在于生产库(历史手工 DDL),Flyway 全部迁移均未定义:
--   * findByRoleAndDeptAndStatus / countByRoleAndDeptAndStatus 直接 SELECT 与 WHERE 这两列
--   * batchUpdateDeptByIds / batchUpdateMembershipByIds 直接 UPDATE 这两列
-- 因此任何按 Flyway 从零构建的环境(本地/CI 服务容器/灾备重建)一碰管理端用户列表
-- 就会 Unknown column。生产因为列已存在而侥幸可用,掩盖了这个缺口。
--
-- 幂等守卫:列已存在时 no-op,不存在时补列,两端部署都安全。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'dept') = 0,
    'ALTER TABLE `user` ADD COLUMN `dept` varchar(100) NULL DEFAULT NULL COMMENT ''社员所属部门名称(与 dept_id 并存,管理端读写走本列)''',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'is_member') = 0,
    'ALTER TABLE `user` ADD COLUMN `is_member` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否已录取为社员:0(否) 1(是)''',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
