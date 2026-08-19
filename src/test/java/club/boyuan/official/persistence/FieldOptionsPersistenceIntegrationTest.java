package club.boyuan.official.persistence;

import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
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

    @Autowired
    private ResumeFieldDefinitionMapper mapper;

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    @Test
    @DisplayName("options 经 BaseMapper 与自定义 XML 查询都能原样读回")
    void optionsSurviveBothQueryPaths() {
        // resume_field_definition.cycle_id 有外键指向 recruitment_cycle,
        // 塞一个编造的周期号会撞 resume_field_definition_ibfk_1,必须先建父行。
        RecruitmentCycle cycle = new RecruitmentCycle();
        cycle.setCycleName("测试-选项持久化");
        cycle.setAcademicYear("2099-2100");
        cycle.setStartDate(java.time.LocalDate.now().minusDays(1));
        cycle.setEndDate(java.time.LocalDate.now().plusDays(1));
        cycle.setIsActive(0);   // 不设启用,免得影响「当前开放周期」相关的其它测试
        cycle.setStatus(1);
        cycleMapper.insert(cycle);
        final Integer testCycleId = cycle.getCycleId();
        assertNotNull(testCycleId, "插入周期后应回填主键");

        ResumeFieldDefinition row = new ResumeFieldDefinition();
        row.setCycleId(testCycleId);
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
            plain.setCycleId(testCycleId);
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
            cycleMapper.deleteById(testCycleId);
        }
    }
}
