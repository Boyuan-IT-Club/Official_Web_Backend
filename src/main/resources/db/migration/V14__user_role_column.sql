-- user.role 遗留列对齐:实体 User(@TableField("role"))与 add() 的占位写入依赖该列,
-- 但它只存在于生产库(历史手工/DDL 添加),Flyway 全部迁移与 official.sql 均未定义——
-- 任何按 Flyway 从零构建的环境(本地/CI 数据库/灾备重建)都会因 Unknown column 'role' 失败。
-- 幂等守卫:列已存在(生产)时本迁移为 no-op,不存在(全新库)时补列,两端部署安全。
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'role') = 0,
    'ALTER TABLE `user` ADD COLUMN `role` varchar(50) NULL DEFAULT NULL COMMENT ''遗留角色占位(RBAC 由 user_role/role 表承担,仅兼容历史写入)''',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;