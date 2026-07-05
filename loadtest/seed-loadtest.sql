-- 压测种子数据：大容量 slot + 600 个可秒杀用户
SET NAMES utf8mb4;

INSERT INTO recruitment_cycle (cycle_id, cycle_name, description, start_date, end_date, academic_year, status, is_active, created_at, updated_at)
VALUES (99, '压测专用周期', 'loadtest only', '2026-01-01', '2026-12-31', '2026', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE cycle_name = VALUES(cycle_name);

INSERT INTO interview_slot (slot_id, cycle_id, interview_date, start_time, end_time, location, interview_type, max_capacity, current_occupied, status, created_at, updated_at)
VALUES (9901, 99, '2026-06-01', '09:00:00', '12:00:00', '压测场', 1, 500, 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE max_capacity = 500, current_occupied = 0, status = 1;

-- 密码 hash 对应明文 loadtest123
SET @pwd = '$2b$10$2Ez3q5GrPlD82CBijNTXheDTXc59m03uXL6iRv2/wmcOSHYc3cks6';

DROP PROCEDURE IF EXISTS seed_loadtest_users;
DELIMITER //
CREATE PROCEDURE seed_loadtest_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 600 DO
        INSERT INTO user (username, password, name, email, phone, major, status, is_deleted, create_time, update_time)
        VALUES (
            CONCAT('loadtest', LPAD(i, 3, '0')),
            @pwd,
            CONCAT('压测用户', i),
            CONCAT('loadtest', LPAD(i, 3, '0'), '@stu.ecnu.edu.cn'),
            CONCAT('1390000', LPAD(i, 4, '0')),
            '软件工程',
            1, 0, NOW(), NOW()
        )
        ON DUPLICATE KEY UPDATE status = 1;

        SET @uid = (SELECT user_id FROM user WHERE username = CONCAT('loadtest', LPAD(i, 3, '0')) LIMIT 1);

        INSERT IGNORE INTO user_role (user_id, role_id, create_time) VALUES (@uid, 4, NOW());

        INSERT INTO resume (user_id, cycle_id, status, resume_score, submitted_at, created_at, updated_at)
        VALUES (@uid, 99, 2, 80, NOW(), NOW(), NOW())
        ON DUPLICATE KEY UPDATE status = 2;

        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL seed_loadtest_users();
DROP PROCEDURE seed_loadtest_users;
