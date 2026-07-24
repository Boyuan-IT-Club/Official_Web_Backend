package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewPreference;
import club.boyuan.official.persistence.entity.InterviewPreferenceTime;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewPreferenceTimeMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionAssignmentServiceImplTest {

    @Mock private IRecruitmentCycleService recruitmentCycleService;
    @Mock private IInterviewPreferenceService interviewPreferenceService;
    @Mock private InterviewPreferenceTimeMapper preferenceTimeMapper;
    @Mock private IInterviewSessionService interviewSessionService;
    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private IInterviewScheduleService interviewScheduleService;
    @Mock private IInterviewTimeSlotService interviewTimeSlotService;
    @Mock private IResumeService resumeService;
    @Mock private ResumeDataService resumeDataService;
    @Mock private DepartmentMapper departmentMapper;

    @InjectMocks
    private SessionAssignmentServiceImpl service;

    /**
     * 场景：3 名候选人都填 [第一志愿=技术部(1), 第二志愿=综合部(2)]，都勾选同一个时间窗。
     * 技术部只有一个容量为 2 的场次，综合部有一个容量为 5 的场次。
     * 期望：前两人进技术部（09:00、09:10 细分），第三人自动降级到综合部（09:00）。
     */
    @Test
    void assign_fillsFirstChoiceThenDowngradesAndSubdividesTime() {
        Integer cycleId = 1;
        when(recruitmentCycleService.getRecruitmentCycleById(cycleId)).thenReturn(new RecruitmentCycle());

        when(resumeService.getAllResumesByCycleId(cycleId)).thenReturn(List.of(
                resume(101, 1), resume(102, 2), resume(103, 3)));
        when(resumeDataService.getResumeName(any(Resume.class))).thenReturn("学生");

        when(interviewScheduleService.list(any(Wrapper.class))).thenReturn(List.of());
        when(interviewPreferenceService.list(any(Wrapper.class))).thenReturn(List.of(
                pref(101, cycleId, 1, 2), pref(102, cycleId, 1, 2), pref(103, cycleId, 1, 2)));

        when(preferenceTimeMapper.selectList(any())).thenReturn(List.of(
                prefTime(101, 10), prefTime(102, 10), prefTime(103, 10)));

        InterviewTimeSlot ts = new InterviewTimeSlot()
                .setTimeSlotId(10).setCycleId(cycleId).setSlotName("周六上午")
                .setInterviewDate(LocalDate.of(2026, 3, 1))
                .setStartTime(LocalTime.of(9, 0)).setEndTime(LocalTime.of(12, 0)).setStatus(1);
        when(interviewTimeSlotService.listByIds(any())).thenReturn(List.of(ts));

        InterviewSession tech = session(1000, cycleId, 10, 1, "301", 2);
        InterviewSession general = session(1001, cycleId, 10, 2, "302", 5);
        when(interviewSessionService.list(any(Wrapper.class))).thenReturn(List.of(tech, general));

        when(departmentMapper.selectList(nullable(Wrapper.class))).thenReturn(List.of(
                dept(1, "技术部"), dept(2, "综合部")));

        SessionAssignmentResultDTO result = service.assign(cycleId);

        assertEquals(3, result.getAssignedCount());
        assertEquals(0, result.getUnassignedCount());

        SessionAssignmentResultDTO.AssignedItem a101 = find(result, 101);
        SessionAssignmentResultDTO.AssignedItem a102 = find(result, 102);
        SessionAssignmentResultDTO.AssignedItem a103 = find(result, 103);

        // 前两人进第一志愿技术部，时间被细分为 09:00 / 09:10
        assertEquals(1, a101.getDeptId());
        assertEquals(1, a101.getMatchedChoice());
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), a101.getInterviewStartTime());
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 10), a101.getInterviewEndTime());

        assertEquals(1, a102.getDeptId());
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 10), a102.getInterviewStartTime());

        // 第三人技术部已满，降级到第二志愿综合部
        assertEquals(2, a103.getDeptId());
        assertEquals(2, a103.getMatchedChoice());
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), a103.getInterviewStartTime());
    }

    /**
     * 场景：候选人志愿部门没有任何匹配时间窗的可用场次 → 进入待调剂名单。
     */
    @Test
    void assign_putsCandidateIntoUnassignedWhenNoMatchingSession() {
        Integer cycleId = 1;
        when(recruitmentCycleService.getRecruitmentCycleById(cycleId)).thenReturn(new RecruitmentCycle());
        when(resumeService.getAllResumesByCycleId(cycleId)).thenReturn(List.of(resume(201, 9)));
        when(resumeDataService.getResumeName(any(Resume.class))).thenReturn("学生");
        when(interviewScheduleService.list(any(Wrapper.class))).thenReturn(List.of());
        when(interviewPreferenceService.list(any(Wrapper.class))).thenReturn(List.of(pref(201, cycleId, 1, null)));
        // 候选人勾选时间窗 10，但技术部场次挂在时间窗 20 上 → 无匹配
        when(preferenceTimeMapper.selectList(any())).thenReturn(List.of(prefTime(201, 10)));
        InterviewTimeSlot ts20 = new InterviewTimeSlot().setTimeSlotId(20).setCycleId(cycleId)
                .setInterviewDate(LocalDate.of(2026, 3, 1)).setStartTime(LocalTime.of(9, 0))
                .setEndTime(LocalTime.of(12, 0)).setStatus(1);
        when(interviewTimeSlotService.listByIds(any())).thenReturn(List.of(ts20));
        when(interviewSessionService.list(any(Wrapper.class))).thenReturn(List.of(session(3000, cycleId, 20, 1, "301", 5)));
        when(departmentMapper.selectList(nullable(Wrapper.class))).thenReturn(List.of(dept(1, "技术部")));

        SessionAssignmentResultDTO result = service.assign(cycleId);

        assertEquals(0, result.getAssignedCount());
        assertEquals(1, result.getUnassignedCount());
        assertEquals(201, result.getUnassigned().get(0).getResumeId());
    }

    // --------------------------------------------------------------- helpers

    private static SessionAssignmentResultDTO.AssignedItem find(SessionAssignmentResultDTO result, int resumeId) {
        SessionAssignmentResultDTO.AssignedItem item = result.getAssigned().stream()
                .filter(a -> a.getResumeId() == resumeId).findFirst().orElse(null);
        assertNotNull(item, "缺少 resumeId=" + resumeId + " 的分配结果");
        return item;
    }

    private static Resume resume(int resumeId, int userId) {
        Resume r = new Resume();
        r.setResumeId(resumeId);
        r.setUserId(userId);
        r.setCycleId(1);
        r.setStatus(2);
        return r;
    }

    private static InterviewPreference pref(int resumeId, int cycleId, Integer first, Integer second) {
        return new InterviewPreference().setResumeId(resumeId).setCycleId(cycleId)
                .setFirstDeptId(first).setSecondDeptId(second);
    }

    private static InterviewPreferenceTime prefTime(int resumeId, int timeSlotId) {
        return new InterviewPreferenceTime().setResumeId(resumeId).setTimeSlotId(timeSlotId);
    }

    private static InterviewSession session(int id, int cycleId, int timeSlotId, int deptId, String loc, int capacity) {
        return new InterviewSession().setSessionId(id).setCycleId(cycleId).setTimeSlotId(timeSlotId)
                .setDeptId(deptId).setLocation(loc).setCapacity(capacity).setCurrentOccupied(0)
                .setInterviewDurationMinutes(10).setStatus(1);
    }

    private static Department dept(int id, String name) {
        Department d = new Department();
        d.setDeptId(id);
        d.setDeptName(name);
        return d;
    }
}
