-- 记录「这封通知当时告诉了他几点」。
--
-- 背景：V51 之后用「通知发送时间 < 安排 updated_at」判断「他收到的是旧安排」，
-- 线上一跑把 97 个人全标成需补发——因为 updated_at 是「这行被写过」，不是
-- 「学生该知道的信息变了」。任何一次写入（同步标记、批量重算、甚至重发通知
-- 本身）都会把它顶上去，用它做判据必然满屏误报，而误报的代价是 97 封重复邮件。
--
-- 换成记录事实本身：发信那一刻把正文里写的面试时间存下来，之后和当前
-- interview_time 直接比。变了就是真变了，不需要推断。
--
-- 历史行为 NULL，一律不判过期——宁可漏报让管理员手工处理几个，
-- 也不能误报群发一百封。
ALTER TABLE `interview_notification_log`
    ADD COLUMN `notified_interview_time` DATETIME NULL
        COMMENT '这封通知里写的面试时间；NULL 表示历史数据，不参与过期判断'
        AFTER `recipient_email`;
