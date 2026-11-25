CREATE SQL SECURITY INVOKER VIEW evaluation_account_grant AS
SELECT DISTINCT u.user_id,u.username,p.permission_code
FROM user u JOIN user_role ur ON u.user_id=ur.user_id
JOIN role r ON r.role_id=ur.role_id AND r.status=1
JOIN role_permission rp ON rp.role_id=r.role_id JOIN permission p ON p.permission_id=rp.permission_id
WHERE u.is_deleted=0 AND u.status=1;

CREATE SQL SECURITY INVOKER VIEW evaluation_board_candidate AS
SELECT s.schedule_id,s.cycle_id,s.resume_id,r.user_id,d.locked
FROM interview_schedule s JOIN resume r ON r.resume_id=s.resume_id AND r.cycle_id=s.cycle_id
JOIN collab_doc d ON d.cycle_id=s.cycle_id AND d.doc_name=CONCAT('eval-board:',s.cycle_id)
WHERE s.status=1 AND (s.user_id IS NULL OR s.user_id=r.user_id);
