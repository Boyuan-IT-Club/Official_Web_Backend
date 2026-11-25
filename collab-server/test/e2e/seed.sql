-- 端到端联调用的最小数据集：一个周期、一个场次、两位面试官、两位候选人。
-- 共编模型的验证重点是「两位面试官改同一行」，因此两人由账号角色直接获得评价权限。
SET NAMES utf8mb4;

INSERT INTO department (dept_id, dept_name, dept_code, status)
VALUES (1, '技术部', 'TECH', 1)
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

INSERT INTO user (user_id, username, password, name, email, dept_id, status)
VALUES
  (7000, 'e2e_admin', 'local-only', '管理员', 'e2e_admin@example.com', 1, 1),
  (7001, 'e2e_interviewer_a', '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '面试官甲', 'e2e_a@example.com', 1, 1),
  (7002, 'e2e_interviewer_b', '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '面试官乙', 'e2e_b@example.com', 1, 1),
  (7003, 'e2e_outsider',      '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '路人丙', 'e2e_c@example.com', 1, 1),
  (8001, 'e2e_candidate_1',   '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '候选人一', 'e2e_s1@example.com', 1, 1),
  (8002, 'e2e_candidate_2',   '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '候选人二', 'e2e_s2@example.com', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO recruitment_cycle (cycle_id, cycle_name, start_date, end_date, academic_year, status, is_active)
VALUES (7003, 'E2E 招募周期', '2026-09-01', '2026-10-01', '2026-2027', 1, 1)
ON DUPLICATE KEY UPDATE cycle_name = VALUES(cycle_name);

INSERT INTO interview_time_slot (time_slot_id, cycle_id, slot_name, interview_date, start_time, end_time, status)
VALUES (7001, 7003, 'E2E 上午场', '2026-09-10', '10:00:00', '12:00:00', 1)
ON DUPLICATE KEY UPDATE slot_name = VALUES(slot_name);

INSERT INTO interview_session (session_id, cycle_id, time_slot_id, dept_id, location, capacity, status)
VALUES (7010, 7003, 7001, 1, '博远楼 301', 10, 1)
ON DUPLICATE KEY UPDATE location = VALUES(location);

INSERT IGNORE INTO user_role(user_id,role_id)
SELECT u.user_id,r.role_id FROM user u JOIN role r ON r.role_code='INTERVIEWER'
WHERE u.user_id IN(7001,7002);
INSERT IGNORE INTO user_role(user_id,role_id)
SELECT 7000,role_id FROM role WHERE role_code='SUPER_ADMIN';

INSERT INTO resume (resume_id, user_id, cycle_id, status, submitted_at)
VALUES (9001, 8001, 7003, 3, NOW()), (9002, 8002, 7003, 3, NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- status=1 才是有效安排，评价表播种只认这一档
INSERT INTO interview_schedule (schedule_id, resume_id, user_id, cycle_id, session_id, dept_id, interview_time, status)
VALUES
  (5001, 9001, 8001, 7003, 7010, 1, '2026-09-10 10:00:00', 1),
  (5002, 9002, 8002, 7003, 7010, 1, '2026-09-10 10:20:00', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- 维度 ID 交给自增：迁移脚本已为既有周期播过默认维度，写死 ID 会撞主键
INSERT INTO evaluation_dimension (cycle_id, name, max_score, weight, sort_order)
VALUES (7003, '技术能力', 10, 2.00, 1), (7003, '沟通表达', 10, 1.00, 2)
ON DUPLICATE KEY UPDATE weight = VALUES(weight), sort_order = VALUES(sort_order);

UPDATE interview_schedule SET session_id=NULL, interview_mode=1 WHERE cycle_id=7003 AND schedule_id=5001;
