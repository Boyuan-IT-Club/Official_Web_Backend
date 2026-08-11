-- 评测提交:候选人 push 派生仓后由 Actions 推送、官网解密入库
-- 身份键 github_username(来自 Actions context,可靠);user_id/cycle_id 可空(未认领/未归周期由管理员关联)
CREATE TABLE IF NOT EXISTS `evaluation_submission`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `github_username` VARCHAR(100) NOT NULL COMMENT 'GitHub 登录名(Actions context,可靠身份键)',
    `user_id`        INT          NULL COMMENT '按用户绑定 github 匹配;NULL=未认领',
    `cycle_id`       INT          NULL COMMENT '入库时取活跃周期;NULL=未归周期',
    `report_sha`     CHAR(64)     NOT NULL COMMENT 'sha256(加密报告单原始字节),幂等去重键',
    `author`         VARCHAR(200) NOT NULL COMMENT '报告单 author(git config user.name,自由文本,仅展示)',
    `evaluated_at`   DATETIME     NOT NULL COMMENT '报告单 timestamp',
    `total_score`    INT          NOT NULL COMMENT '总分 0-400',
    `max_score`      INT          NOT NULL DEFAULT 400 COMMENT '满分',
    `task1_score`    INT          NULL,
    `task2_score`    INT          NULL,
    `task3_score`    INT          NULL,
    `task4_score`    INT          NULL,
    `report_json`    JSON         NOT NULL COMMENT '解密后的完整明文 JSON',
    `repository`     VARCHAR(300) NULL COMMENT '候选人生成仓 URL',
    `commit_sha`     CHAR(40)     NULL COMMENT '推送 commit',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_sha` (`report_sha`),
    KEY `idx_user` (`user_id`),
    KEY `idx_cycle` (`cycle_id`),
    KEY `idx_github` (`github_username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评测提交';