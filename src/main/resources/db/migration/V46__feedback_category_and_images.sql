-- 问题反馈补两样：分类与截图。
--
-- 只有一个 content 字段时，管理端看到的是一堆没法归类的纯文本——
-- 「页面报错」和「希望加个功能」混在一起，既分不出轻重也没法分派。
-- 截图则是反馈里最有用的东西：一张图省掉三轮「你点的是哪个按钮」。
--
-- 为什么不改 V45 而新开一版：V45 虽然还没合进 main，但同事本地多半已经跑过，
-- 直接改会让 flyway 校验和对不上，需要手工 repair——那个坑不值得省这一个文件。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND COLUMN_NAME = 'category') = 0,
    'ALTER TABLE `feedback` ADD COLUMN `category` VARCHAR(32) NOT NULL DEFAULT ''other'' COMMENT ''反馈分类:bug/suggestion/other'' AFTER `user_id`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 截图存 COS 的 objectKey，不存图本身：与简历照片/附件同一套存储，
-- 数据库里只留引用。最多三张，JSON 数组够用，不值得单开一张表。
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND COLUMN_NAME = 'image_keys') = 0,
    'ALTER TABLE `feedback` ADD COLUMN `image_keys` TEXT NULL COMMENT ''截图的 COS objectKey 数组(JSON)'' AFTER `content`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 按分类筛选是管理端的主要用法，补一个索引
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND INDEX_NAME = 'idx_feedback_category') = 0,
    'ALTER TABLE `feedback` ADD INDEX `idx_feedback_category` (`category`, `is_deleted`, `created_at`)',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
