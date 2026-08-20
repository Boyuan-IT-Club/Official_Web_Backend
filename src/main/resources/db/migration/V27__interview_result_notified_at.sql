-- 录取结果通知留痕：加 notified_at。
--
-- 此前发送通知不落任何痕迹 —— 管理端看不出谁通知过谁没通知过，
-- 再点一次发送会对所有勾选的人原样重发邮件，也没有任何提示。
-- 重发时该列更新为最近一次发送时间。
--
-- 幂等守卫：沿用 V14/V20/V25/V26 的写法，列已存在时 no-op。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_result'
       AND COLUMN_NAME = 'notified_at') = 0,
    'ALTER TABLE `interview_result` ADD COLUMN `notified_at` datetime NULL COMMENT ''最近一次结果通知的发送时间,NULL=从未通知''',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
