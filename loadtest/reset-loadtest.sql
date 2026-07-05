-- 每轮压测前重置：清空预约、库存、Outbox（cycle=99, slot=9901）
SET NAMES utf8mb4;

DELETE FROM interview_schedule WHERE cycle_id = 99;
DELETE FROM message_outbox WHERE aggregate_type IN ('INTERVIEW_BOOKING', 'FEISHU_SYNC');

UPDATE interview_slot
SET current_occupied = 0, status = 1, updated_at = NOW()
WHERE slot_id = 9901;
