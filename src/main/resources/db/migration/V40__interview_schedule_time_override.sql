-- 面试安排：标记 interview_time 是否为人工指定，避免后续公式重算静默覆盖。
--
-- 背景：interview_time 原本只能由「场次时间窗起点 + 场内序号 × 单人时长」公式算出，
-- 管理员无法把某位候选人精确调到几点几分（学生说「14:00 有课改 15:30」就无解）。
-- 新增「手动调整面试时间」能力后，用本列区分来源：
--   0 = 公式生成（自动分配 / 人工调剂换场，默认）
--   1 = 人工指定（管理员手动调整的精确时刻）
--
-- 取舍：一键自动分配(assign)本就不处理「已安排」的人，天然不会覆盖已存在安排；
-- 真正会重算覆盖的是「人工调剂(换场)」，而换场已经改变了时间窗/日期，
-- 保留旧的人工时间语义错误，故换场时把本列重置回 0（回到公式重算）。
--
-- 幂等守卫：沿用 V14/V20/V25/V26/V27 的写法，列已存在时 no-op。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_schedule'
       AND COLUMN_NAME = 'time_overridden') = 0,
    'ALTER TABLE `interview_schedule`
       ADD COLUMN `time_overridden` TINYINT(1) NOT NULL DEFAULT 0
       COMMENT ''是否人工指定面试时间：0=公式生成，1=人工指定''
       AFTER `interview_time`',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
