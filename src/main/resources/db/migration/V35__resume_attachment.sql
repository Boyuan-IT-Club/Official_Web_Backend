-- 简历附件：学生在投递表单末尾上传的任意格式资料，供面试官查看。
--
-- 为什么单独一张表而不是塞进 resume_field_value：
-- 附件是「多条 + 带元信息（原始文件名/类型/大小）」的结构，
-- 挤进一个 text 字段就得自己序列化，删单个附件、按类型判断能否预览
-- 都变成字符串操作。
--
-- 挂 resume_id 而不是 user_id：同一个人每届各投一份简历，
-- 附件属于「这一届投的这份」，跨届不该串。
--
-- 文件本身进 COS 私有桶，这里只存对象键。附件是申请人的个人资料，
-- 与头像/活动图不同，绝不能进 /api/files 的公开白名单
-- （见 PublicFilePrefixWhitelistTest 的 INTENTIONALLY_PRIVATE）。

CREATE TABLE IF NOT EXISTS `resume_attachment` (
    `id`            int          NOT NULL AUTO_INCREMENT,
    `resume_id`     int          NOT NULL COMMENT '所属简历',
    `user_id`       int          NOT NULL COMMENT '上传者,用于鉴权时快速判断本人',
    `cycle_id`      int          NULL     COMMENT '冗余的周期,按届筛选附件时免去回表',
    `file_name`     varchar(255) NOT NULL COMMENT '原始文件名,下载时用它做文件名',
    `object_key`    varchar(512) NOT NULL COMMENT 'COS 对象键',
    `content_type`  varchar(150) NULL     COMMENT '上传时的 MIME,决定能否内联预览',
    `size_bytes`    bigint       NOT NULL DEFAULT 0,
    `created_at`    timestamp    NULL     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 按简历列附件是唯一的高频查询
    KEY `idx_resume` (`resume_id`),
    -- 同一份简历里不允许重名，否则面试官看到两个「作品集.pdf」无从分辨
    UNIQUE KEY `uk_resume_filename` (`resume_id`, `file_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='简历附件';
