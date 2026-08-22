-- 端到端联调用的最小数据集：一个周期、一个部门、一场面试、两位候选人、两位面试官。
-- 仅供本地 E2E 验证使用，不参与生产迁移。
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO department (dept_id, dept_name, dept_code)
VALUES (1, '技术部', 'TECH')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

-- 两位面试官 + 两位候选人
INSERT INTO user (user_id, username, password, email, name, dept_id, status)
VALUES (7, '2024007', 'x', 'i7@boyuan.club', '张三', 1, 1),
       (9, '2024009', 'x', 'i9@boyuan.club', '李四', 1, 1),
       (801, '2024801', 'x', 'c801@boyuan.club', '候选人甲', 1, 1),
       (802, '2024802', 'x', 'c802@boyuan.club', '候选人乙', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 面试官角色绑定（role_id 5 = INTERVIEWER，由 V10 播种）
INSERT IGNORE INTO user_role (user_id, role_id) VALUES (7, 5), (9, 5);

INSERT INTO recruitment_cycle (cycle_id, cycle_name, start_date, end_date, academic_year, is_active)
VALUES (1, '2026 秋季招新', '2026-09-01', '2026-10-01', '2026-2027', 1)
ON DUPLICATE KEY UPDATE is_active = 1;

INSERT INTO resume (resume_id, user_id, cycle_id, status)
VALUES (1001, 801, 1, 2), (1002, 802, 1, 2)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO interview_time_slot (time_slot_id, cycle_id, slot_name, interview_date, start_time, end_time)
VALUES (1, 1, '第一场', '2026-09-10', '14:00:00', '17:00:00')
ON DUPLICATE KEY UPDATE slot_name = VALUES(slot_name);

INSERT INTO interview_session (session_id, cycle_id, time_slot_id, dept_id, location, capacity)
VALUES (1, 1, 1, 1, '理科大楼 B226', 20)
ON DUPLICATE KEY UPDATE location = VALUES(location);

-- status=1 表示「已安排」，评价表只播种这个状态的行
INSERT INTO interview_schedule (schedule_id, resume_id, cycle_id, user_id, session_id, dept_id, interview_time, status)
VALUES (100, 1001, 1, 801, 1, 1, '2026-09-10 14:00:00', 1),
       (101, 1002, 1, 802, 1, 1, '2026-09-10 14:20:00', 1)
ON DUPLICATE KEY UPDATE interview_time = VALUES(interview_time), status = 1;

-- 两位面试官都绑到这一场：评价表里各自拥有一列
INSERT IGNORE INTO session_interviewer (session_id, user_id) VALUES (1, 7), (1, 9);
