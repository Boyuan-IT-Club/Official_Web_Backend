-- 一个面试场次可以同时面多个部门的同学。
--
-- 现实里一场面试常常几个部门一起面（面试官坐一屋、候选人轮流进），
-- 而原来 interview_session.dept_id 是单值，只能拆成几个场次重复录入
-- 时间、地点、容量，且容量还被切碎了。
--
-- 用关联表而不是在 session 上存 CSV：分配算法要「按部门找场次」
-- （statesByDept），CSV 得在内存里再拆一遍，还没法建索引。
--
-- interview_session.dept_id 保留不动：
--   1) 历史数据与飞书同步、导出等处仍在读它
--   2) 它继续充当「主部门」，多部门场次取第一个，用于那些只能显示一个
--      部门的地方（如场次列表的紧凑视图）
-- 真正的判定一律以本表为准，dept_id 只是兼容与展示。

CREATE TABLE IF NOT EXISTS `interview_session_dept` (
    `id`         int NOT NULL AUTO_INCREMENT,
    `session_id` int NOT NULL,
    `dept_id`    int NOT NULL,
    PRIMARY KEY (`id`),
    -- 同一场次不允许重复挂同一个部门
    UNIQUE KEY `uk_session_dept` (`session_id`, `dept_id`),
    -- 分配算法按部门捞场次，这是最热的查询方向
    KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='面试场次覆盖的部门（一场可面多个部门）';

-- 回填：存量场次每个都只服务自己那一个部门，语义不变。
-- 不回填的话本表为空，分配算法按新逻辑会认为「没有任何场次服务任何部门」，
-- 一键分配当场全军覆没。
INSERT IGNORE INTO `interview_session_dept` (`session_id`, `dept_id`)
SELECT `session_id`, `dept_id` FROM `interview_session` WHERE `dept_id` IS NOT NULL;
