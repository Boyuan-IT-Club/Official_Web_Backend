-- 录取/未录取通知可以重发。
--
-- 原来 interview_notification_log 上有 UNIQUE (notification_type, result_id)：
-- 同一份结果的模板邮件只能有一条日志，第二次发送在代码层被 alreadySent 跳过，
-- 管理端却照样显示「已通知」——邮件没到，界面看着成功。
--
-- 去重的本意是防 MQ 重投（acknowledge-mode: auto 下 SMTP 超时会触发重投），
-- 那要按「这一次发送」去重，而不是按「这份结果」——后者把管理员有意的重发
-- 也一并拦掉了。改为每次入队生成 request_id，按它做唯一约束。
--
-- 只动结果那条唯一键。uk_type_schedule 保留：场次预约成功通知是系统自动触发的，
-- 一个场次只发一次才是对的语义。
--
-- 注意顺序：必须先加列与新唯一键、再删旧唯一键。反过来若中途失败，
-- 表会处于「没有任何去重约束」的状态，MQ 一重投就无限发邮件。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_notification_log'
       AND COLUMN_NAME = 'request_id') = 0,
    'ALTER TABLE `interview_notification_log` ADD COLUMN `request_id` VARCHAR(64) NULL COMMENT ''这一次发送的唯一标识,用于去重 MQ 重投'' AFTER `result_id`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- MySQL 的唯一索引允许多个 NULL，历史行没有 request_id 也不会冲突
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_notification_log'
       AND INDEX_NAME = 'uk_request_id') = 0,
    'ALTER TABLE `interview_notification_log` ADD UNIQUE KEY `uk_request_id` (`request_id`)',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_notification_log'
       AND INDEX_NAME = 'uk_type_result') > 0,
    'ALTER TABLE `interview_notification_log` DROP INDEX `uk_type_result`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
