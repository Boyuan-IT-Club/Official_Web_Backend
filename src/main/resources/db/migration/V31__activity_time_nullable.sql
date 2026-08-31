-- 活动起止日期允许为空。
--
-- V6 里 start_time/end_time 是 NOT NULL 且无默认值,而管理端表单从未强制填写
-- 起止日期——不填就创建直接撞 "Field 'start_time' doesn't have a default value"
-- (线上实测)。「时间待定」的活动是真实场景:官网活动页对空时间本来就渲染
-- 「时间待定」,进行中判断(start<=now<=end)对 NULL 自然不命中,放开即可。

ALTER TABLE `activity`
    MODIFY COLUMN `start_time` date NULL COMMENT '开始时间(空=时间待定)',
    MODIFY COLUMN `end_time`   date NULL COMMENT '结束时间(空=时间待定)';
