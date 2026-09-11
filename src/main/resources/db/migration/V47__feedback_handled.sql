-- 反馈的「已处理」标记。
--
-- 只标不回：管理员需要的是「这条我看过、处理完了」，好让列表默认只剩待办；
-- 回复提交人是另一回事（要有通知渠道、要防骚扰），本次不做。
--
-- 记 handled_by / handled_at 而不只是一个布尔：几个人一起看反馈时，
-- 「谁标的、什么时候标的」是能省掉一轮对话的信息。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND COLUMN_NAME = 'handled') = 0,
    'ALTER TABLE `feedback` ADD COLUMN `handled` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 未处理 / 1 已处理'' AFTER `image_keys`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND COLUMN_NAME = 'handled_by') = 0,
    'ALTER TABLE `feedback` ADD COLUMN `handled_by` INT NULL COMMENT ''标记为已处理的管理员'' AFTER `handled`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND COLUMN_NAME = 'handled_at') = 0,
    'ALTER TABLE `feedback` ADD COLUMN `handled_at` DATETIME NULL COMMENT ''标记时间'' AFTER `handled_by`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 「只看未处理」是管理端的默认视图，走这个索引
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback'
       AND INDEX_NAME = 'idx_feedback_handled') = 0,
    'ALTER TABLE `feedback` ADD INDEX `idx_feedback_handled` (`handled`, `is_deleted`, `created_at`)',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
