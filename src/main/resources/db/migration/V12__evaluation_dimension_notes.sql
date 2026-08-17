-- 面试评价：每个维度独立的文字评语与作者。
--
-- 背景：V10 建的 interview_evaluation 只有一个 comment 总评框，面试官得把
-- 「技术能力」「项目经历」「沟通表达」「意愿匹配」几项的话揉进一段文字里，
-- 事后既分不清哪句针对哪一项，也看不出是谁写的（哪怕 contributors 记了参与人，
-- 那也只是行级的「有谁动过手」，不是「这一项是谁评的」）。
--
-- 现在每个维度一格评语，署名随之落到维度级。数据来源是协同服务的
-- writer-tracker——它本就按单元格记录写入者（取 Yjs update 的 origin，
-- 不是客户端自报，无法伪造）。
--
-- 为什么不把评语塞进已有的 scores JSON（改成 {dimensionId: {score, note}}）：
-- 那会破坏所有现有读取方（评价汇总、结果与通知、前端加权总分算法都按
-- {dimensionId: score} 解析）。新增两列是向后兼容的，旧数据读起来不受影响。

ALTER TABLE `interview_evaluation`
    ADD COLUMN `dimension_notes` json NULL
        COMMENT '各维度独立评语 {dimensionId: text}' AFTER `scores`,
    ADD COLUMN `dimension_writers` json NULL
        COMMENT '各维度评价的作者 {dimensionId: userId}，取自协同服务的单元格级写入记录' AFTER `dimension_notes`;
