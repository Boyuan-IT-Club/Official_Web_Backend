-- 给 resume_field_definition 补 options 列。
--
-- 缺这一列的后果：管理端「简历字段」里能编辑下拉/单选/多选的选项，前端类型
-- (BackendResumeField.options) 也带着它，但库里无处存放 —— 保存时被静默丢掉。
-- 于是投递页的下拉选项只能靠前端写死的常量（DEFAULT_GRADE / DEFAULT_GENDER 等），
-- 管理员改了配置也不生效，而且界面上看不出任何异常。
--
-- 用 JSON 类型（MySQL 8）：这一列存的是字符串数组 ["男","女"]，
-- 实体侧用 MyBatis-Plus 的 JacksonTypeHandler 映射成 List<String>。
--
-- 幂等守卫：沿用 V14/V20 的写法，列已存在时 no-op。

SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume_field_definition'
       AND COLUMN_NAME = 'options') = 0,
    'ALTER TABLE `resume_field_definition` ADD COLUMN `options` json NULL COMMENT ''下拉/单选/多选的选项列表,JSON 字符串数组''',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
