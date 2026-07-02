-- 支持学生提交多个候选面试时段，并按志愿部门分配最终面试场。
SET @db = DATABASE();

-- interview_slot.dept_id：面试场归属部门；NULL 表示共享/调剂场。
SET @slot_dept_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'interview_slot'
      AND COLUMN_NAME = 'dept_id'
);
SET @sql = IF(@slot_dept_col_exists = 0,
    'ALTER TABLE interview_slot ADD COLUMN dept_id INT NULL COMMENT ''归属部门ID，NULL表示共享/调剂面试场'' AFTER location',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @slot_dept_idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'interview_slot'
      AND INDEX_NAME = 'idx_slot_dept'
);
SET @sql = IF(@slot_dept_idx_exists = 0,
    'CREATE INDEX idx_slot_dept ON interview_slot (dept_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- interview_schedule.preferred_slot_ids：学生提交的候选 slotId 列表，JSON 数组字符串。
SET @schedule_preferred_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'interview_schedule'
      AND COLUMN_NAME = 'preferred_slot_ids'
);
SET @sql = IF(@schedule_preferred_col_exists = 0,
    'ALTER TABLE interview_schedule ADD COLUMN preferred_slot_ids TEXT NULL COMMENT ''学生提交的候选面试时段ID列表(JSON数组)'' AFTER slot_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- interview_schedule.assigned_dept_id：最终按哪个部门志愿安排。
SET @schedule_dept_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'interview_schedule'
      AND COLUMN_NAME = 'assigned_dept_id'
);
SET @sql = IF(@schedule_dept_col_exists = 0,
    'ALTER TABLE interview_schedule ADD COLUMN assigned_dept_id INT NULL COMMENT ''按志愿分配得到的部门ID'' AFTER preferred_slot_ids',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @schedule_dept_idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'interview_schedule'
      AND INDEX_NAME = 'idx_schedule_assigned_dept'
);
SET @sql = IF(@schedule_dept_idx_exists = 0,
    'CREATE INDEX idx_schedule_assigned_dept ON interview_schedule (assigned_dept_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
