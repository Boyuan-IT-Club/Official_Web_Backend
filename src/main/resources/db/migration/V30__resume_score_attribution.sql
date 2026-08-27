-- 简历打分署名。
--
-- resume_score 此前只有分数没有出处,管理端要求「超管/管理员能看到是谁打的分」。
-- scored_by 记打分人 userId,scored_at 记时间;两列都可空——历史分数无从追溯,
-- 新打的分从部署后开始署名。不加外键:打分人账号被删不该牵连简历行。

ALTER TABLE `resume`
    ADD COLUMN `scored_by` int NULL DEFAULT NULL COMMENT '打分人 userId(署名,无外键)' AFTER `resume_score`,
    ADD COLUMN `scored_at` datetime NULL DEFAULT NULL COMMENT '打分时间' AFTER `scored_by`;
