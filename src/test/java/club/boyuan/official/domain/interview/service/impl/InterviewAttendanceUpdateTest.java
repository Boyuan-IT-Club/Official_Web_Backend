package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.domain.interview.dto.UpdateAttendanceRequestDTO;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper;
import club.boyuan.official.persistence.mapper.ResumeFieldValueMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生改「能否到线下参加面试」。
 *
 * 线上问题：选了「不能线下」的同学被分配阶段想改回线下，但该选择只能在
 * 简历表单里改——简历一进评审或投递期一结束就锁死，学生被卡在线上名单里。
 * 新接口绕开简历锁，只在「已排上场次」时拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewAttendanceUpdateTest {

    @Mock private IResumeService resumeService;
    @Mock private club.boyuan.official.domain.interview.service.IInterviewTimeSlotService interviewTimeSlotService;
    @Mock private club.boyuan.official.persistence.mapper.InterviewPreferenceTimeMapper preferenceTimeMapper;
    @Mock private club.boyuan.official.persistence.mapper.DepartmentMapper departmentMapper;
    @Mock private InterviewScheduleMapper interviewScheduleMapper;
    @Mock private ResumeFieldDefinitionMapper resumeFieldDefinitionMapper;
    @Mock private ResumeFieldValueMapper resumeFieldValueMapper;

    @InjectMocks
    private InterviewPreferenceServiceImpl service;

    private ResumeFieldValue stored;

    private static UpdateAttendanceRequestDTO req(boolean offline, String note) {
        UpdateAttendanceRequestDTO r = new UpdateAttendanceRequestDTO();
        r.setCycleId(6);
        r.setCanAttendOffline(offline);
        r.setCustomTime(note);
        return r;
    }

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, InterviewSchedule.class);
        TableInfoHelper.initTableInfo(assistant, ResumeFieldDefinition.class);
        TableInfoHelper.initTableInfo(assistant, ResumeFieldValue.class);

        Resume resume = new Resume();
        resume.setResumeId(33);
        resume.setCycleId(6);
        resume.setStatus(3);   // 评审中——简历表单已锁，这里必须仍然放行
        when(resumeService.getResumeByUserIdAndCycleId(7, 6)).thenReturn(resume);
        when(interviewScheduleMapper.exists(any())).thenReturn(false);

        ResumeFieldDefinition def = new ResumeFieldDefinition();
        def.setFieldId(88);
        def.setCycleId(6);
        def.setFieldKey("expected_interview_time");
        when(resumeFieldDefinitionMapper.selectOne(any())).thenReturn(def);

        stored = new ResumeFieldValue();
        stored.setValueId(5);
        stored.setResumeId(33);
        stored.setFieldId(88);
        stored.setFieldValue("{\"first\":\"技术部\",\"second\":\"综合部\",\"canAttend\":\"yes\",\"customTime\":\"\"}");
        when(resumeFieldValueMapper.selectOne(any())).thenReturn(stored);
        when(resumeFieldValueMapper.updateById(any(ResumeFieldValue.class))).thenReturn(1);
        when(resumeFieldValueMapper.insert(any(ResumeFieldValue.class))).thenReturn(1);
    }

    @Test
    @DisplayName("切到线上：canAttend=no、说明写入，志愿部门原样保留")
    void switchToOnlineKeepsDepartments() throws Exception {
        service.updateAttendance(7, req(false, "在外地实习，周末线上可以"));

        ArgumentCaptor<ResumeFieldValue> cap = ArgumentCaptor.forClass(ResumeFieldValue.class);
        verify(resumeFieldValueMapper).updateById(cap.capture());
        JsonNode node = new ObjectMapper().readTree(cap.getValue().getFieldValue());
        assertEquals("no", node.path("canAttend").asText());
        assertEquals("在外地实习，周末线上可以", node.path("customTime").asText());
        // 合并而不是覆盖：面试意向卡当年一起写进去的志愿部门不能丢
        assertEquals("技术部", node.path("first").asText());
        assertEquals("综合部", node.path("second").asText());
    }

    @Test
    @DisplayName("改回线下：canAttend=yes，旧的线上原因清空，不残留在待约名单")
    void switchBackToOfflineClearsNote() throws Exception {
        stored.setFieldValue("{\"first\":\"技术部\",\"canAttend\":\"no\",\"customTime\":\"以前不方便\"}");
        service.updateAttendance(7, req(true, null));

        ArgumentCaptor<ResumeFieldValue> cap = ArgumentCaptor.forClass(ResumeFieldValue.class);
        verify(resumeFieldValueMapper).updateById(cap.capture());
        JsonNode node = new ObjectMapper().readTree(cap.getValue().getFieldValue());
        assertEquals("yes", node.path("canAttend").asText());
        assertEquals("", node.path("customTime").asText());
    }

    @Test
    @DisplayName("已排上生效场次：拒绝，改时间要走改期申请")
    void rejectedWhenScheduled() {
        when(interviewScheduleMapper.exists(any())).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.updateAttendance(7, req(false, null)));
    }

    @Test
    @DisplayName("字段值行不存在时插入新行（意向卡从没保存过的老简历）")
    void insertsWhenValueMissing() {
        when(resumeFieldValueMapper.selectOne(any())).thenReturn(null);
        service.updateAttendance(7, req(false, "x"));
        verify(resumeFieldValueMapper).insert(any(ResumeFieldValue.class));
    }

    @Test
    @DisplayName("坏 JSON 不卡死：当空对象重建")
    void corruptJsonRebuilt() throws Exception {
        stored.setFieldValue("not-json{{{");
        service.updateAttendance(7, req(false, "说明"));
        ArgumentCaptor<ResumeFieldValue> cap = ArgumentCaptor.forClass(ResumeFieldValue.class);
        verify(resumeFieldValueMapper).updateById(cap.capture());
        JsonNode node = new ObjectMapper().readTree(cap.getValue().getFieldValue());
        assertEquals("no", node.path("canAttend").asText());
    }
}
