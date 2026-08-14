-- 修复 evaluation_submission 与 user 表的 collation 不一致:
-- V10 建表未显式指定 COLLATE,继承了 MySQL 8 服务器默认 utf8mb4_0900_ai_ci,
-- 而 user 表(V6,显式 utf8mb4_unicode_ci)的 github 列与其做 join 比较时报
-- Illegal mix of collations。统一转换为 utf8mb4_unicode_ci,与核心表对齐。
ALTER TABLE `evaluation_submission`
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;