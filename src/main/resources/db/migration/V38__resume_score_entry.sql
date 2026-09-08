-- 简历打分明细：一份简历可由多位管理员各打一分，取平均作为简历分。
--
-- 为什么单独一张表：resume.resume_score 只有一个格子，第二个人打分
-- 会直接覆盖第一个人的；要「谁打了几分」可追溯，明细必须逐条落库。
-- resume 表原有三列保持原语义的聚合视图：resume_score 存平均分（四舍五入），
-- scored_by / scored_at 存最近一次打分的人和时间，飞书导出与列表排序零改动。

CREATE TABLE IF NOT EXISTS `resume_score_entry` (
    `id`         int      NOT NULL AUTO_INCREMENT,
    `resume_id`  int      NOT NULL COMMENT '所属简历',
    `scorer_id`  int      NOT NULL COMMENT '打分人 userId',
    `score`      int      NOT NULL COMMENT '0~100',
    `created_at` datetime NOT NULL COMMENT '首次打分时间',
    `updated_at` datetime NOT NULL COMMENT '最近一次改分时间',
    PRIMARY KEY (`id`),
    -- 一人对一份简历只有一票，改分是 UPDATE 不是再插一条
    UNIQUE KEY `uk_resume_scorer` (`resume_id`, `scorer_id`),
    KEY `idx_scorer` (`scorer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='简历打分明细（多人打分，resume.resume_score 存平均）';

-- 把既有的单人打分搬进明细表，平均分不变。NOT EXISTS 保证重跑幂等。
INSERT INTO `resume_score_entry` (`resume_id`, `scorer_id`, `score`, `created_at`, `updated_at`)
SELECT r.`resume_id`, r.`scored_by`, r.`resume_score`,
       COALESCE(r.`scored_at`, NOW()), COALESCE(r.`scored_at`, NOW())
FROM `resume` r
WHERE r.`scored_by` IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `resume_score_entry` e
      WHERE e.`resume_id` = r.`resume_id` AND e.`scorer_id` = r.`scored_by`
  );
