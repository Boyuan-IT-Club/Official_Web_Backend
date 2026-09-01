-- 飞书多维表格「按地点分桶」的链接配置。
--
-- 背景：推送面试安排到飞书本来就是按地点分桶、每个地点推到各自的表格，
-- 但分桶逻辑（FeishuImportExecutor）走的是 interview_schedule.slot_id -> interview_slot，
-- 那是方案A（学生自助抢时段）的旧模型：
--   * 方案B 的一键分配显式把 slot_id 写成 NULL，只写 session_id
--   * interview_slot 生产 0 行，前端也不再维护它
--   于是 slotById.get(null) == null，所有排期都被静默 skip —— 推送实际一条也发不出去。
--
-- 方案B 里地点在 interview_session.location 上，而同一个地点通常对应多个场次
-- （不同部门、不同时间窗），所以链接不挂在场次上，而是按「周期 × 地点」配置：
--   * 分桶键就是地点，配置粒度与分桶粒度一致
--   * 同地点的多个场次自动共享一个链接，不必重复填，也不会出现同地点两个场次填了不同链接的矛盾状态
--   * 场次增删不影响链接配置

CREATE TABLE IF NOT EXISTS `cycle_location_feishu_table`
(
    `id`               int          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cycle_id`         int          NOT NULL COMMENT '招募周期ID',
    `location`         varchar(255) NOT NULL COMMENT '面试地点，取自 interview_session.location（线上场次约定为「线上面试」）',
    `feishu_table_url` varchar(500) NOT NULL COMMENT '该地点对应的飞书多维表格链接',
    `remark`           varchar(255) NULL COMMENT '备注，如负责人、表格用途',
    `created_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cycle_location` (`cycle_id`, `location`),
    KEY `idx_clft_cycle` (`cycle_id`),
    CONSTRAINT `fk_clft_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '周期×地点 → 飞书多维表格链接';

-- 历史数据迁移：把旧模型里配过的链接搬过来，避免已配置的信息丢失。
-- interview_slot 目前 0 行，这条大概率什么都不搬，但保留以防其它环境有数据。
INSERT IGNORE INTO `cycle_location_feishu_table` (`cycle_id`, `location`, `feishu_table_url`, `remark`)
SELECT s.`cycle_id`,
       COALESCE(NULLIF(TRIM(s.`location`), ''), IF(s.`interview_type` = 2, '线上面试', '未指定地点')),
       s.`feishu_table_url`,
       '由 V11 从 interview_slot 迁入'
FROM `interview_slot` s
WHERE s.`feishu_table_url` IS NOT NULL
  AND s.`feishu_table_url` <> '';
