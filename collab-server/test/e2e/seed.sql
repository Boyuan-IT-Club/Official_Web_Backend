-- 端到端联调用的最小数据集：一个周期、一个场次、两位面试官、两位候选人。
-- 共编模型的验证重点是「两位面试官改同一行」，因此两人都绑定在同一场次上。
SET NAMES utf8mb4;

INSERT INTO department (dept_id, dept_name, dept_code, status)
VALUES (1, '技术部', 'TECH', 1)
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

INSERT INTO user (user_id, username, password, name, email, dept_id, status)
VALUES
  (7001, 'e2e_interviewer_a', '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '面试官甲', 'e2e_a@example.com', 1, 1),
  (7002, 'e2e_interviewer_b', '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '面试官乙', 'e2e_b@example.com', 1, 1),
  (7003, 'e2e_outsider',      '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '路人丙', 'e2e_c@example.com', 1, 1),
  (8001, 'e2e_candidate_1',   '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '候选人一', 'e2e_s1@example.com', 1, 1),
  (8002, 'e2e_candidate_2',   '$2a$10$e2eplaceholderhashe2eplaceholderhashe2eplaceholder', '候选人二', 'e2e_s2@example.com', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO recruitment_cycle (cycle_id, cycle_name, start_date, end_date, academic_year, status, is_active)
VALUES (3, 'E2E 招募周期', '2026-09-01', '2026-10-01', '2026-2027', 1, 1)
ON DUPLICATE KEY UPDATE cycle_name = VALUES(cycle_name);

INSERT INTO interview_time_slot (time_slot_id, cycle_id, slot_name, interview_date, start_time, end_time, status)
VALUES (1, 3, 'E2E 上午场', '2026-09-10', '10:00:00', '12:00:00', 1)
ON DUPLICATE KEY UPDATE slot_name = VALUES(slot_name);

INSERT INTO interview_session (session_id, cycle_id, time_slot_id, dept_id, location, capacity, status)
VALUES (10, 3, 1, 1, '博远楼 301', 10, 1)
ON DUPLICATE KEY UPDATE location = VALUES(location);

-- 面试官绑定：甲、乙负责场次 10，丙谁也不负责（用于验证越权剔除）
INSERT INTO session_interviewer (session_id, user_id)
SELECT * FROM (SELECT 10 AS s, 7001 AS u UNION ALL SELECT 10, 7002) t
WHERE NOT EXISTS (SELECT 1 FROM session_interviewer si WHERE si.session_id = t.s AND si.user_id = t.u);

INSERT INTO resume (resume_id, user_id, cycle_id, status, submitted_at)
VALUES (9001, 8001, 3, 3, NOW()), (9002, 8002, 3, 3, NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- status=1 才是有效安排，评价表播种只认这一档
INSERT INTO interview_schedule (schedule_id, resume_id, user_id, cycle_id, session_id, dept_id, interview_time, status)
VALUES
  (5001, 9001, 8001, 3, 10, 1, '2026-09-10 10:00:00', 1),
  (5002, 9002, 8002, 3, 10, 1, '2026-09-10 10:20:00', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- 维度 ID 交给自增：迁移脚本已为既有周期播过默认维度，写死 ID 会撞主键
INSERT INTO evaluation_dimension (cycle_id, name, max_score, weight, sort_order)
VALUES (3, '技术能力', 10, 2.00, 1), (3, '沟通表达', 10, 1.00, 2)
ON DUPLICATE KEY UPDATE weight = VALUES(weight), sort_order = VALUES(sort_order);
