-- 通知日志补上简历维度。
--
-- 原来这张表只认 schedule_id 与 result_id，够用是因为通知都挂在面试安排或
-- 录取结果上。简历初筛未通过通知（V43 起）两者都没有——它发生在排面试之前，
-- 收件人根本还没有安排，也还没进结果名单。于是这类通知只写得进一行没有主体的
-- 日志，管理端问「这个人的初筛未通过通知发了没」时无从查起，只能靠人记。
--
-- 加一列 resume_id 并建普通索引（不是唯一键）：初筛通知允许重发，
-- 去重仍由 V37 的 request_id 唯一键负责，只拦 MQ 重投。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_notification_log'
       AND COLUMN_NAME = 'resume_id') = 0,
    'ALTER TABLE `interview_notification_log` ADD COLUMN `resume_id` INT NULL COMMENT ''简历ID,面试前发出的通知(如简历初筛未通过)用它定位收件人'' AFTER `result_id`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_notification_log'
       AND INDEX_NAME = 'idx_notif_resume') = 0,
    'ALTER TABLE `interview_notification_log` ADD INDEX `idx_notif_resume` (`resume_id`, `notification_type`)',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
