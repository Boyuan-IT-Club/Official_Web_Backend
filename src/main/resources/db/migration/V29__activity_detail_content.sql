-- 活动图文详情。
--
-- description(text) 保留为列表卡片上的纯文本摘要;新列 detail_content 存富文本 HTML
-- (管理端所见即所得编辑器产出,图片以 URL 形式内嵌,指向 COS/本地上传的对象)。
-- 服务端在写入前用 jsoup 白名单消毒,库里只会出现受控标签——用户端直接渲染。
-- 用 MEDIUMTEXT:一篇带十几张图说明的活动记录轻松超过 TEXT 的 64KB。

ALTER TABLE `activity`
    ADD COLUMN `detail_content` MEDIUMTEXT NULL COMMENT '图文详情(富文本HTML,服务端已按白名单消毒)' AFTER `description`;
