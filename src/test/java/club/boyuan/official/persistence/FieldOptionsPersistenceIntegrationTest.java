package club.boyuan.official.persistence;

import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 下拉/单选的选项(options)必须真的能存进去、读出来。
 *
 * 此前 resume_field_definition 压根没有 options 列:管理端能编辑选项、
 * 前端类型也带着它,但保存时被静默丢掉 —— 投递页的下拉只能靠前端写死的常量,
 * 管理员改了配置不生效,而且界面上看不出任何异常。V25 补了该列。
 *
 * 这条链有两个容易漏的点,都由本测试覆盖:
 *   1. 实体 @TableName 必须带 autoResultMap = true,否则 BaseMapper 查询
 *      不应用 JacksonTypeHandler,读出来永远是 null;
 *   2. ResumeFieldDefinitionMapper.xml 的 resultMap 必须显式声明该 TypeHandler,
 *      自定义查询(findByCycleId / findById)不会继承实体上的注解。
 */
@SpringBootTest
class FieldOptionsPersistenceIntegrationTest {

    /** 与真实数据错开,避免与任何招募周期撞号 */
    private static final int TEST_CYCLE_ID = 990901;

    @Autowired
    private ResumeFieldDefinitionMapper mapper;

    @Test
    @DisplayName("options 经 BaseMapper 与自定义 XML 查询都能原样读回")
    void optionsSurviveBothQueryPaths() {
        ResumeFieldDefinition row = new ResumeFieldDefinition();
        row.setCycleId(TEST_CYCLE_ID);
        row.setFieldKey("gender");
        row.setFieldLabel("性别");
        row.setFieldType("radio");
        row.setIsRequired(true);
        row.setSortOrder(1);
        row.setIsActive(true);
        row.setOptions(List.of("男", "女"));

        mapper.insert(row);
        Integer id = row.getFieldId();
        assertNotNull(id, "插入后应回填主键");

        try {
            // 路径一：BaseMapper.selectById —— 依赖实体上的 autoResultMap
            ResumeFieldDefinition viaBase = mapper.selectById(id);
            assertNotNull(viaBase.getOptions(),
                    "BaseMapper 读不到 options：检查 @TableName 是否带 autoResultMap = true");
            assertEquals(List.of("男", "女"), viaBase.getOptions());

            // 路径二：自定义 XML findById —— 依赖 resultMap 里显式声明的 TypeHandler
            ResumeFieldDefinition viaXml = mapper.findById(id);
            assertNotNull(viaXml.getOptions(),
                    "自定义 XML 读不到 options：resultMap 里漏了 typeHandler 声明");
            assertEquals(List.of("男", "女"), viaXml.getOptions());

            // 非选项类字段允许为空，不该被强制写成空数组
            ResumeFieldDefinition plain = new ResumeFieldDefinition();
            plain.setCycleId(TEST_CYCLE_ID);
            plain.setFieldKey("name");
            plain.setFieldLabel("姓名");
            plain.setFieldType("text");
            plain.setIsRequired(true);
            plain.setSortOrder(2);
            plain.setIsActive(true);
            mapper.insert(plain);
            try {
                assertNull(mapper.selectById(plain.getFieldId()).getOptions(),
                        "文本字段的 options 应保持为 null");
            } finally {
                mapper.deleteById(plain.getFieldId());
            }
        } finally {
            mapper.deleteById(id);
        }
    }
}
