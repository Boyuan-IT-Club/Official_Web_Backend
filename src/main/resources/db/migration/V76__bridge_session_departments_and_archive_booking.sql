-- Source-compatible while old and new website images share the primary.
DELIMITER $$
CREATE TRIGGER interview_session_primary_insert BEFORE INSERT ON interview_session FOR EACH ROW
BEGIN
    IF JSON_LENGTH(NEW.dept_ids)>0 THEN
        SET NEW.dept_id=JSON_VALUE(NEW.dept_ids,'$[0]' RETURNING SIGNED);
    ELSEIF NEW.dept_id IS NOT NULL THEN
        SET NEW.dept_ids=JSON_ARRAY(NEW.dept_id);
    END IF;
END$$
CREATE TRIGGER interview_session_primary_update BEFORE UPDATE ON interview_session FOR EACH ROW
BEGIN
    IF JSON_LENGTH(NEW.dept_ids)>0 THEN
        SET NEW.dept_id=JSON_VALUE(NEW.dept_ids,'$[0]' RETURNING SIGNED);
    END IF;
END$$

-- Only the retired booking queue's consumer can use this entry point.
CREATE PROCEDURE delivery_archive_booking(IN source_key VARCHAR(128),IN envelope JSON)
BEGIN
    IF source_key IS NULL OR CHAR_LENGTH(TRIM(source_key))=0 OR envelope IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Booking archive requires a stable identity and its original envelope';
    END IF;
    INSERT INTO message_delivery(delivery_kind,event_type,aggregate_type,aggregate_id,payload,queue_state,mail_state,
          published_at,review_evidence)
      VALUES('TASK','INTERVIEW_BOOKING_PERSIST','INTERVIEW_BOOKING',source_key,envelope,'PUBLISHED','NA',
          NOW(),JSON_OBJECT('retiredBooking',envelope,'observedAt',NOW(6),'disposition','ARCHIVED_FOR_REVIEW'))
      ON DUPLICATE KEY UPDATE queue_state='PUBLISHED',published_at=COALESCE(published_at,NOW()),
          queue_lease_token=NULL,queue_lease_until=NULL,
          review_evidence=IF(JSON_CONTAINS_PATH(COALESCE(review_evidence,JSON_OBJECT()),'one','$.retiredBooking'),
             review_evidence,JSON_MERGE_PATCH(COALESCE(review_evidence,JSON_OBJECT()),
             JSON_OBJECT('retiredBooking',envelope,'observedAt',NOW(6),'disposition','ARCHIVED_FOR_REVIEW')));
END$$
DELIMITER ;
