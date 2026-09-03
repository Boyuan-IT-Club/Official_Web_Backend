-- 让「没有面试安排」的候选人也能有录取结果。
--
-- 背景：interview_result.schedule_id 是 NOT NULL，意味着结果行必须挂在一场
-- 面试上。于是这两类人在「结果与通知」里根本不存在，管理员无从录取或婉拒：
--   1. 选了「不能参加线下面试」的同学（不进排期，自然没有 schedule）
--   2. 因人数/时间原因始终没被分配到场次的同学
-- 线上实测周期 6 的丁华烨、周期 3 的叶晓良都属于这种情况。
--
-- 改动：schedule_id 放开为可空，另存 resume_id 与 cycle_id，让结果行能独立成立。
-- MySQL 的唯一索引允许多个 NULL，所以原来 schedule_id 上的唯一约束不会
-- 阻止多条「无安排」结果并存；真正防重复的是新加的 (cycle_id, resume_id)。

SET @s = IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_result'
       AND COLUMN_NAME = 'schedule_id') = 'NO',
    'ALTER TABLE `interview_result` MODIFY COLUMN `schedule_id` int NULL COMMENT ''面试安排ID；无面试安排（如不能线下参加）时为空''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_result'
       AND COLUMN_NAME = 'resume_id') = 0,
    'ALTER TABLE `interview_result` ADD COLUMN `resume_id` int NULL COMMENT ''简历ID，结果脱离面试安排时靠它定位候选人''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_result'
       AND COLUMN_NAME = 'cycle_id') = 0,
    'ALTER TABLE `interview_result` ADD COLUMN `cycle_id` int NULL COMMENT ''招募周期ID；此前只能经 schedule 反查，无安排时查不到''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 存量回填：老数据都有 schedule，从它反查
UPDATE interview_result r
JOIN interview_schedule s ON s.schedule_id = r.schedule_id
SET r.resume_id = COALESCE(r.resume_id, s.resume_id),
    r.cycle_id  = COALESCE(r.cycle_id, s.cycle_id)
WHERE r.resume_id IS NULL OR r.cycle_id IS NULL;

-- 同一周期同一份简历只允许一条结果
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_result'
       AND INDEX_NAME = 'uk_cycle_resume') = 0,
    'ALTER TABLE `interview_result` ADD UNIQUE KEY `uk_cycle_resume` (`cycle_id`, `resume_id`)',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
