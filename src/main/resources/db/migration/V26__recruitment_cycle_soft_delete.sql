-- 招募周期改为软删除:加 is_deleted 标记。
--
-- 为什么不能硬删:
--   1. resume.cycle_id 是 ON DELETE RESTRICT —— 有简历的周期根本删不动,
--      管理端点删除只会得到一个外键报错(用户实际撞到的就是它);
--   2. 更危险的是 interview_session / interview_time_slot 对周期是 ON DELETE CASCADE,
--      一旦某个周期没有简历而删除成功,整个周期的场次、时间窗、面试安排会被
--      静默连带清掉 —— 这两种结局没有一种是想要的。
--
-- 软删语义:列表/搜索/开放周期等一切"枚举"查询过滤 is_deleted = 0;
-- 按 ID 的点查(findById / BaseMapper.selectById)刻意不过滤 ——
-- 历史简历、评价表要靠它解析周期名。
--
-- 幂等守卫:沿用 V14/V20/V25 的写法,列已存在时 no-op。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruitment_cycle'
       AND COLUMN_NAME = 'is_deleted') = 0,
    'ALTER TABLE `recruitment_cycle` ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记:0(正常) 1(已删除,列表不再展示但历史数据保留)''',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
