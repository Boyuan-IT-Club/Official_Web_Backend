-- 简历打分评语：每位打分人在自己那一票上附几句理由。
--
-- 挂在 resume_score_entry 上而不是另开一张表：评语就是「我这一票」的一部分——
-- 一人一份简历一条（沿用 uk_resume_scorer），撤销打分时随这一票一起删除，
-- 展示时和分数、打分人、时间一起出现，不需要额外关联。
ALTER TABLE `resume_score_entry`
    ADD COLUMN `comment` VARCHAR(500) NULL COMMENT '打分评语（可选，≤500 字）' AFTER `score`;
