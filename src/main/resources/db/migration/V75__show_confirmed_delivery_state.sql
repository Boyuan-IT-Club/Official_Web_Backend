CREATE SQL SECURITY INVOKER VIEW delivery_target_status AS
WITH targets AS (
    SELECT i.cycle_id,i.result_id AS target_id,i.result_id,i.resume_id,NULL AS schedule_id,NULL AS revision,
        IF(i.decision=1,'ADMISSION','REJECTION') AS typ
        FROM interview_result i WHERE i.decision IN (1,2)
    UNION ALL SELECT r.cycle_id,r.resume_id,NULL,r.resume_id,NULL,NULL,'RESUME_REJECTED' FROM resume r WHERE r.status=5
    UNION ALL SELECT s.cycle_id,s.schedule_id,NULL,s.resume_id,s.schedule_id,s.notice_revision,k.typ
        FROM interview_schedule s CROSS JOIN (SELECT 'BOOKING_SUCCESS' typ UNION ALL SELECT 'EVE_REMINDER' UNION ALL SELECT 'DAY_REMINDER') k
        WHERE s.status=1 AND s.interview_mode=0
), evidence AS (
    SELECT t.*, (
       SELECT m.delivery_id FROM message_delivery m
       WHERE m.delivery_kind IN ('HISTORY','AUTO','REQUEST') AND m.notification_type=t.typ AND (
        (t.result_id IS NOT NULL AND m.result_id=t.result_id) OR
        (t.typ='RESUME_REJECTED' AND m.resume_id=t.resume_id) OR
        (t.schedule_id IS NOT NULL AND m.schedule_id=t.schedule_id AND m.schedule_revision=t.revision))
       ORDER BY m.created_at DESC,m.delivery_id DESC LIMIT 1
    ) latest
    FROM targets t
)
SELECT t.cycle_id,t.target_id,t.typ,
  CASE WHEN EXISTS(SELECT 1 FROM message_delivery h WHERE h.delivery_kind='REVIEW' AND h.review_resolution='OPEN'
    AND (h.review_scope='GLOBAL' OR (h.review_scope='TYPE' AND h.notification_type=t.typ)
      OR (h.review_scope='RESULT' AND h.result_id=t.result_id)
      OR (h.review_scope='RESUME' AND h.resume_id=t.resume_id AND (h.notification_type IS NULL OR h.notification_type=t.typ))
      OR (h.review_scope='SCHEDULE' AND h.schedule_id=t.schedule_id AND h.notification_type=t.typ
          AND (h.review_through_revision IS NULL OR t.revision<=h.review_through_revision)))) THEN 'UNKNOWN'
    WHEN EXISTS(SELECT 1 FROM message_delivery h WHERE h.delivery_kind='REVIEW' AND h.review_resolution='CONFIRMED_SENT'
      AND ((h.review_scope='SCHEDULE' AND h.schedule_id=t.schedule_id AND h.notification_type=t.typ AND h.review_through_revision=t.revision)
        OR (m.request_id IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(h.review_evidence,'$.confirmedRequestId'))=m.request_id)
        OR (m.delivery_id IS NULL AND h.notification_type=t.typ AND JSON_EXTRACT(h.review_evidence,'$.confirmedRequestId') IS NOT NULL
            AND ((t.result_id IS NOT NULL AND h.result_id=t.result_id) OR (t.typ='RESUME_REJECTED' AND h.resume_id=t.resume_id))))) THEN 'SENT'
    WHEN m.queue_state='FAILED' AND m.mail_state='PENDING' THEN 'FAILED'
    ELSE COALESCE(m.mail_state,'UNSENT') END AS mail_state,
  m.mail_sent_at AS confirmed_at
FROM evidence t LEFT JOIN message_delivery m ON m.delivery_id=t.latest;
