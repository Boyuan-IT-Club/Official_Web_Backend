CREATE SQL SECURITY INVOKER VIEW legacy_interview_slot_source AS
SELECT t.slot_id,t.cycle_id,JSON_OBJECT('slotId',t.slot_id,'cycleId',t.cycle_id,
    'interviewDate',t.interview_date,'startTime',t.start_time,'endTime',t.end_time,
    'location',t.location,'interviewType',t.interview_type,'meetingLink',t.meeting_link,
    'maxCapacity',t.max_capacity,'currentOccupied',t.current_occupied,'feishuTableUrl',t.feishu_table_url,
    'status',t.status,'createdAt',t.created_at,'updatedAt',t.updated_at) AS snapshot
FROM interview_slot t;
UPDATE interview_schedule s JOIN legacy_interview_slot_source v ON s.slot_id=v.slot_id AND s.cycle_id=v.cycle_id
SET s.legacy_slot_snapshot=v.snapshot,s.updated_at=s.updated_at WHERE s.legacy_slot_snapshot IS NULL;
