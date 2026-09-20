package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeFieldValueMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交简历时校验必填项。
 *
 * 线上事故：个人照片与意向部门都配成了必填，却各有同学没填就提交成功了
 * （管理端卡片显示没有头像、「部门:未提供」）。前端两个洞——照片的 required
 * 只声明了 prop 从没用过，意向部门是表单外的自定义控件、没选还静默跳过——
 * 而后端这里当时一项都不校验，于是没有任何一层拦得住。
 */
class ResumeRequiredFieldsOnSubmitTest {

    private ResumeMapper resumeMapper;
    private ResumeFieldValueMapper valueMapper;
    private IResumeFieldDefinitionService definitionService;
    private ResumeServiceImpl service;

    private static final int CYCLE_ID = 14;
    private static final int RESUME_ID = 158;

    @BeforeEach
    void setUp() {
        resumeMapper = mock(ResumeMapper.class);
        valueMapper = mock(ResumeFieldValueMapper.class);
        definitionService = mock(IResumeFieldDefinitionService.class);
        RecruitmentCycleMapper cycleMapper = mock(RecruitmentCycleMapper.class);

        service = new ResumeServiceImpl(
                resumeMapper,
                cycleMapper,
                mock(club.boyuan.official.persistence.mapper.UserMapper.class),
                mock(club.boyuan.official.persistence.mapper.ResumeScoreEntryMapper.class),
                valueMapper,
                definitionService,
                redisTemplate());

        Resume resume = new Resume();
        resume.setResumeId(RESUME_ID);
        resume.setCycleId(CYCLE_ID);
        resume.setStatus(1);
        when(resumeMapper.findById(RESUME_ID)).thenReturn(resume);

        // 周期开放，好让校验走到必填这一步
        RecruitmentCycle cycle = new RecruitmentCycle();
        cycle.setCycleId(CYCLE_ID);
        cycle.setIsActive(1);
        cycle.setStartDate(LocalDate.now().minusDays(1));
        cycle.setEndDate(LocalDate.now().plusDays(7));
        when(cycleMapper.selectById(anyInt())).thenReturn(cycle);
    }

    /** clearCacheByCycleId 会用到；keys() 返回 null 时代码已自己判空 */
    @SuppressWarnings("unchecked")
    private static org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate() {
        return mock(org.springframework.data.redis.core.RedisTemplate.class);
    }

    private static ResumeFieldDefinition def(int id, String key, String label, boolean required) {
        ResumeFieldDefinition d = new ResumeFieldDefinition();
        d.setFieldId(id);
        d.setCycleId(CYCLE_ID);
        d.setFieldKey(key);
        d.setFieldLabel(label);
        d.setIsRequired(required);
        return d;
    }

    private static ResumeFieldValue val(int fieldId, String value) {
        ResumeFieldValue v = new ResumeFieldValue();
        v.setResumeId(RESUME_ID);
        v.setFieldId(fieldId);
        v.setFieldValue(value);
        return v;
    }

    private void given(List<ResumeFieldDefinition> defs, List<ResumeFieldValue> values) {
        when(definitionService.getFieldDefinitionsByCycleId(CYCLE_ID)).thenReturn(defs);
        when(valueMapper.findByResumeId(RESUME_ID)).thenReturn(values);
    }

    @Test
    @DisplayName("必填项齐全：照常提交")
    void submitsWhenComplete() {
        given(
                Arrays.asList(def(1, "name", "姓名", true), def(2, "personal_photo", "个人照片", true)),
                Arrays.asList(val(1, "赵安堃"), val(2, "resume-photos/x.jpg")));

        service.submitResume(RESUME_ID);

        verify(resumeMapper).updateById(any(Resume.class));
    }

    @Test
    @DisplayName("没传照片就提交：拒绝，并点名是哪一项")
    void rejectsMissingPhoto() {
        given(
                Arrays.asList(def(1, "name", "姓名", true), def(2, "personal_photo", "个人照片", true)),
                Arrays.asList(val(1, "赵安堃")));

        BusinessException e = assertThrows(BusinessException.class, () -> service.submitResume(RESUME_ID));

        assertTrue(e.getMessage().contains("个人照片"), "要说清缺的是哪一项，实际: " + e.getMessage());
        verify(resumeMapper, never()).updateById(any(Resume.class));
    }

    @Test
    @DisplayName("意向部门存成空数组 [] 也算没填 —— 清空多选后存的就是它")
    void emptyJsonArrayCountsAsMissing() {
        given(
                Arrays.asList(def(3, "expected_departments", "意向部门", true)),
                Arrays.asList(val(3, "[]")));

        BusinessException e = assertThrows(BusinessException.class, () -> service.submitResume(RESUME_ID));
        assertTrue(e.getMessage().contains("意向部门"));
    }

    @Test
    @DisplayName("缺多项时一次列全，别让人来回试")
    void listsAllMissingAtOnce() {
        given(
                Arrays.asList(
                        def(1, "name", "姓名", true),
                        def(2, "personal_photo", "个人照片", true),
                        def(3, "expected_departments", "意向部门", true)),
                new ArrayList<>());

        BusinessException e = assertThrows(BusinessException.class, () -> service.submitResume(RESUME_ID));
        assertTrue(e.getMessage().contains("姓名"));
        assertTrue(e.getMessage().contains("个人照片"));
        assertTrue(e.getMessage().contains("意向部门"));
    }

    @Test
    @DisplayName("选填项没填不拦")
    void optionalFieldsDoNotBlock() {
        given(
                Arrays.asList(def(1, "name", "姓名", true), def(9, "github", "GitHub", false)),
                Arrays.asList(val(1, "赵安堃")));

        service.submitResume(RESUME_ID);

        verify(resumeMapper).updateById(any(Resume.class));
    }

    @Test
    @DisplayName("面试意向三项虽配成必填也要放过 —— 它们存在 interview_preference，不在简历字段表")
    void interviewPreferenceKeysAreExempt() {
        given(
                Arrays.asList(
                        def(1, "name", "姓名", true),
                        def(16, "can_attend_offline_interview", "能否线下面试", true),
                        def(17, "expected_interview_time", "第一面试时间", true),
                        def(18, "second_interview_time", "第二面试时间", true)),
                Arrays.asList(val(1, "赵安堃")));

        service.submitResume(RESUME_ID);

        verify(resumeMapper).updateById(any(Resume.class));
    }

    @Test
    @DisplayName("第一/第二志愿也要放过 —— 值合并进 expected_departments 存，自己那两列始终为空")
    void choiceKeysAreExempt() {
        given(
                Arrays.asList(
                        def(13, "first_choice", "第一志愿", true),
                        def(14, "second_choice", "第二志愿", true),
                        def(15, "expected_departments", "意向部门", true)),
                Arrays.asList(val(15, "[\"技术部\",\"综合部\"]")));

        service.submitResume(RESUME_ID);

        verify(resumeMapper).updateById(any(Resume.class));
    }

    @Test
    @DisplayName("错误码用「缺少必填项」，别被 3008 吞成笼统的提交失败")
    void usesMissingRequiredFieldCode() {
        given(
                Arrays.asList(def(2, "personal_photo", "个人照片", true)),
                new ArrayList<>());

        BusinessException e = assertThrows(BusinessException.class, () -> service.submitResume(RESUME_ID));
        assertEquals(BusinessExceptionEnum.MISSING_REQUIRED_FIELD.getCode(), e.getCode());
    }

    @Test
    @DisplayName("空白判定：null / 空串 / 纯空格 / [] / \"null\" 都算没填")
    void blankValueRules() {
        assertTrue(ResumeServiceImpl.isBlankFieldValue(null));
        assertTrue(ResumeServiceImpl.isBlankFieldValue(""));
        assertTrue(ResumeServiceImpl.isBlankFieldValue("   "));
        assertTrue(ResumeServiceImpl.isBlankFieldValue("[]"));
        assertTrue(ResumeServiceImpl.isBlankFieldValue("null"));
        assertFalse(ResumeServiceImpl.isBlankFieldValue("0"));
        assertFalse(ResumeServiceImpl.isBlankFieldValue("[\"技术部\"]"));
    }
}
