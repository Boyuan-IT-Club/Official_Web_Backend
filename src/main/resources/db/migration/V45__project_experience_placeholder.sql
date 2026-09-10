-- 简历「项目经验」引导填完整 GitHub 仓库地址：提升 agent 入口瀑布
-- URL 直配与归属核对命中率(#146 B-AG2 依赖该地址)。
-- 按 field_key 更新(覆盖各周期定义,幂等可重放)。
UPDATE resume_field_definition
SET placeholder = '请填写项目经验，如有 GitHub 仓库请完整填写仓库地址。示例：参与社团官网的后端开发：https://github.com/Boyuan-IT-Club/Official_Web_Backend，主要负责后端接口与数据库设计。'
WHERE field_key IN ('projects', 'project_experience');
