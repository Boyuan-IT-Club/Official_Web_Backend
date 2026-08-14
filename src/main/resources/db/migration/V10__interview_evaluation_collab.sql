-- 协同面试评价表（方案一）：面试评价从飞书多维表格迁回管理端，多位面试官在同一张表上实时协同。
-- 编辑期真源是协同服务持有的 Y.Doc（CRDT），本迁移建立的是「快照存放处」与「物化落库目标」：
--   collab_doc / collab_audit   —— 协同文档快照与写入审计
--   evaluation_dimension        —— 每届可配置的评分类别（表格的列定义）
--   interview_evaluation        —— 物化后的评价数据，**一场面试一份，多位面试官共同编辑**
--   session_interviewer         —— 场次绑定面试官，用于「我的场次」过滤与写入权限判定
-- 落库后业务真源即为 MySQL，下游（评价汇总/结果与通知/AI 写回）感知不到上游是 CRDT。

SET @db := DATABASE();

-- ---------------------------------------------------------------------------
-- 0. 修复 schema 漂移：interview_schedule.user_id
--    InterviewSchedule 实体与方案B分配代码（SessionAssignmentServiceImpl）都在写 user_id，
--    但 V6 建表未含该列、V7 只补了 session_id/dept_id，从未有迁移添加过它。
--    评价表按 schedule_id 组织并需回查候选人（姓名/部门等），依赖该列，故在此幂等补齐。
-- ---------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'interview_schedule'
      AND COLUMN_NAME = 'user_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE interview_schedule ADD COLUMN user_id INT NULL COMMENT ''候选人用户ID（冗余，来自 resume）'' AFTER resume_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史数据回填：老记录的 user_id 为空，从 resume 带出
UPDATE `interview_schedule` s
    JOIN `resume` r ON r.`resume_id` = s.`resume_id`
SET s.`user_id` = r.`user_id`
WHERE s.`user_id` IS NULL;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'interview_schedule'
      AND INDEX_NAME = 'idx_schedule_user'
);
SET @idx := IF(@idx_exists = 0,
    'CREATE INDEX idx_schedule_user ON interview_schedule (user_id)',
    'SELECT 1');
PREPARE stmt2 FROM @idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ---------------------------------------------------------------------------
-- 1. 评分维度模板：每届可配置，播种协同表格时生成初始列
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `evaluation_dimension`
(
    `dimension_id` int          NOT NULL AUTO_INCREMENT COMMENT '维度ID',
    `cycle_id`     int          NOT NULL COMMENT '招募周期ID',
    `name`         varchar(50)  NOT NULL COMMENT '维度名称，如「技术能力」',
    `max_score`    int          NOT NULL DEFAULT 10 COMMENT '该维度满分',
    `weight`       decimal(4, 2) NOT NULL DEFAULT 1.00 COMMENT '加权总分中的权重',
    `sort_order`   int          NOT NULL DEFAULT 0 COMMENT '列顺序（升序）',
    `created_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`dimension_id`),
    UNIQUE KEY `uk_cycle_name` (`cycle_id`, `name`),
    KEY `idx_dim_cycle` (`cycle_id`),
    CONSTRAINT `fk_dim_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `recruitment_cycle` (`cycle_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '面试评分维度模板（每届可配置）';

-- ---------------------------------------------------------------------------
-- 2. 面试评价：**一位候选人一行，多位面试官共同编辑同一份**
--    协作模式（已与业务确认）：同场次的几位面试官面同一个候选人，通常由其中一位主记录，
--    但任何一位都可以补充记录、修改分数——针对一个候选人始终只有一份评价。
--    因此唯一键是 schedule_id（不按面试官拆行），并发写同一字段由协同层的 CRDT 收敛，
--    评语在编辑期是 Y.Text（字符级合并），物化到本表时落为纯文本。
--    scores 用 JSON 存 {dimensionId: score}，避免每加一个评分类别就改表结构；
--    total_score 按权重预先算好冗余存储，供汇总排序时不必在 SQL 里解 JSON。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_evaluation`
(
    `eval_id`        int      NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `schedule_id`    int      NOT NULL COMMENT '面试安排ID（一场面试一份评价）',
    `cycle_id`       int      NOT NULL COMMENT '招募周期ID（冗余，便于按届查询）',
    `resume_id`      int      NOT NULL COMMENT '简历ID（冗余）',
    `scores`         json     NULL COMMENT '各评分类别得分 {dimensionId: score}',
    `total_score`    decimal(6, 2) NULL COMMENT '按权重算好的加权总分（冗余）',
    `comment`        text     NULL COMMENT '面试记录与评语（多位面试官共同编辑的结果）',
    `recommendation` tinyint  NULL COMMENT '共同结论：1倾向通过 2待定 3不倾向',
    `status`         tinyint  NOT NULL DEFAULT 1 COMMENT '1进行中 2已定稿',
    `ai_suggestion`  json     NULL COMMENT 'AI 总评（建议分/理由/风险点），由方案二写入，仅供参考',
    `contributors`   json     NULL COMMENT '参与编辑过的面试官 userId 列表（由协同审计聚合，用于署名与追责）',
    `last_edited_by` int      NULL COMMENT '最后修改人 userId',
    `submitted_by`   int      NULL COMMENT '点击定稿的面试官 userId',
    `submitted_at`   datetime NULL COMMENT '定稿时间',
    `version`        bigint   NOT NULL DEFAULT 0 COMMENT '物化版本号（协同服务给出的单调值，通常为时间戳），用于丢弃迟到的旧快照',
    `created_at`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`eval_id`),
    UNIQUE KEY `uk_eval_schedule` (`schedule_id`),
    KEY `idx_eval_cycle` (`cycle_id`),
    KEY `idx_eval_last_editor` (`last_edited_by`),
    CONSTRAINT `fk_eval_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `interview_schedule` (`schedule_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '面试评价（一场面试一份，多位面试官共同编辑）';

-- ---------------------------------------------------------------------------
-- 3. 场次绑定面试官：谁负责面哪一场
--    现有 interview_session 只有部门+地点+容量，没有「面试官」概念，这里补上。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `session_interviewer`
(
    `id`         int      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id` int      NOT NULL COMMENT '面试场次ID',
    `user_id`    int      NOT NULL COMMENT '面试官用户ID',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_user` (`session_id`, `user_id`),
    KEY `idx_si_user` (`user_id`),
    CONSTRAINT `fk_si_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`session_id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '面试场次与面试官绑定';

-- ---------------------------------------------------------------------------
-- 4. 协同文档快照：Yjs encodeStateAsUpdate 的二进制状态
--    协同服务防抖写入（onStoreDocument），服务重启后据此恢复文档，不引入额外中间件。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collab_doc`
(
    `doc_name`   varchar(64) NOT NULL COMMENT '文档名，如 eval-board:3',
    `cycle_id`   int         NOT NULL COMMENT '招募周期ID',
    `state`      longblob    NULL COMMENT 'Yjs 文档二进制快照；NULL 表示尚未持久化，协同服务会拉播种数据初始化',
    `locked`     tinyint     NOT NULL DEFAULT 0 COMMENT '0可编辑 1已锁定（周期出结果后冻结）',
    `created_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`doc_name`),
    KEY `idx_collab_cycle` (`cycle_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '协同文档快照（Y.Doc）';

-- ---------------------------------------------------------------------------
-- 5. 协同写入审计
--    同场次内是共享编辑（谁都可写），但跨场次的隔离 CRDT 协议层管不了——已连接的客户端理论上
--    能写任意单元格。因此靠「UI 约束 + 物化校验（发起人须在该场次面试官名单内）+ 审计」三层兜底，
--    这张表是第三层：记录每次变更由谁发起、影响了哪些行列键、是否因越权被丢弃。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collab_audit`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `doc_name`     varchar(64)  NOT NULL COMMENT '文档名',
    `user_id`      int          NOT NULL COMMENT '发起变更的用户ID（取自 update 的 origin）',
    `touched_keys` varchar(512) NULL COMMENT '本次变更影响的 行/列 键摘要',
    `rejected`     tinyint      NOT NULL DEFAULT 0 COMMENT '0正常 1物化时因越权被丢弃',
    `created_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_doc_time` (`doc_name`, `created_at`),
    KEY `idx_audit_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '协同文档写入审计';

-- ---------------------------------------------------------------------------
-- 6. 权限与角色种子（沿用 V6 风格：显式 ID + INSERT IGNORE）
--    interview:evaluate —— 面试官填写/修改自己的评价；管理员的 resume:audit 可读全部并配置维度。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `permission` (`permission_id`, `permission_name`, `permission_code`, `resource_identifier`,
                                 `description`, `create_time`, `update_time`)
VALUES (11, '面试评价', 'interview:evaluate', NULL, '在自己负责的场次内填写与修改候选人面试评价（共享编辑）', NOW(), NOW());

INSERT IGNORE INTO `role` (`role_id`, `role_name`, `role_code`, `description`, `status`, `create_time`, `update_time`)
VALUES (5, '面试官', 'INTERVIEWER', '负责面试并记录评价；可读写自己所在场次的全部候选人评价（一场一份，与同场面试官共同编辑）', 1, NOW(), NOW());

-- 面试官角色获得评价权限；管理员与超管一并获得（他们也要能进评价表）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`)
VALUES (5, 11, NOW()),
       (1, 11, NOW()),
       (2, 11, NOW());

-- ---------------------------------------------------------------------------
-- 7. 默认评分维度：为所有启用中的周期播下四个维度，避免开表时列为空
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `evaluation_dimension` (`cycle_id`, `name`, `max_score`, `weight`, `sort_order`)
SELECT c.`cycle_id`, d.`name`, d.`max_score`, d.`weight`, d.`sort_order`
FROM `recruitment_cycle` c
         JOIN (SELECT '技术能力' AS `name`, 10 AS `max_score`, 1.00 AS `weight`, 1 AS `sort_order`
               UNION ALL
               SELECT '沟通表达', 10, 1.00, 2
               UNION ALL
               SELECT '学习潜力', 10, 1.00, 3
               UNION ALL
               SELECT '意愿匹配', 10, 1.00, 4) d
WHERE c.`is_active` = 1;
