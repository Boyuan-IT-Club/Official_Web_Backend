-- 线上面试。
--
-- 背景：有同学排完场次后才发现当天来不及到校，只能申请改期，但他真正想要的
-- 不是换时间而是「改成线上」。此前系统里完全没有线上这个概念——
-- interview_schedule 的 16 个列里没有任何线上/线下标记，「能否线下面试」
-- 只存在于简历字段 expected_interview_time 的一段 JSON 里，而且那是投递时的
-- 意向，排期之后改不了。管理员只能在群里私下约，系统一无所知：
-- 进度页照旧显示教室，提醒邮件照旧让人去教室。
--
-- 三处改动，各自的落点不同：

-- 1) 会议链接放在周期上，不放在每条安排上。
--    和候场教室同一个道理——全周期共用一个腾讯会议号，逐条填既是重复劳动，
--    又会因为填错一条而让某个人进错房间。
ALTER TABLE `recruitment_cycle`
    ADD COLUMN `online_meeting_link` VARCHAR(512) NULL
        COMMENT '线上面试会议链接，全周期共用；留空表示本届不支持线上' AFTER `waiting_room`;

-- 2) 安排上只标一个「这场是线上还是线下」。
--    不新建线上场次：线上面试多是一对一临时约的，塞进 session 模型要造一堆
--    假的时间窗和容量，和它本身的灵活性冲突。
ALTER TABLE `interview_schedule`
    ADD COLUMN `interview_mode` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=线下 1=线上' AFTER `time_overridden`;

-- 3) 改期申请要能表达「我想改成线上」。
--    和「换个时间」是两种诉求，处理方式也不同：换时间要取消原安排等重排，
--    改线上则保留安排、只把 mode 翻过去并释放原场次名额。
ALTER TABLE `interview_reschedule_request`
    ADD COLUMN `request_type` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=改时间 1=改为线上' AFTER `preferred_time_slot_ids`;
