-- 报告单已升级为 task1–task5 / 总分 500(工具仓 2026-08-11 上线,task5 为新增环境检查项)
-- 补 task5 得分列,与 task1-4 对齐;分数明细始终以 report_json 为准
ALTER TABLE `evaluation_submission`
    ADD COLUMN `task5_score` INT NULL COMMENT 'task5 得分(满分100)' AFTER `task4_score`;