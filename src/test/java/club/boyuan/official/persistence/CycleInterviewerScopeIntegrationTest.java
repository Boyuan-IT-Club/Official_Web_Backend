package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.InterviewTimeSlotMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归保护:面试官的评价表准入必须按招募周期隔离。
 *
 * 修复前的漏洞:协同服务的 onAuthenticate 只校验「令牌里有没有 interview:evaluate」,
 * 不校验「这个人是不是这个周期的面试官」。协同文档名形如 eval-board:{cycleId},
 * 于是 A 周期的面试官把周期号一改就能打开 B 周期的评价表,
 * 看到 B 周期的全部候选人名单与分数。
 *
 * interview_session 有三个外键(周期、时间窗、部门),所以必须建齐父行 ——
 * 直接塞不存在的 ID 会撞 fk_sess_cycle。部门用 V6 种子里的 1(技术部)。
 */
@SpringBootTest
class CycleInterviewerScopeIntegrationTest {

    private static final int SEEDED_DEPT_ID = 1;
    private static final int INTERVIEWER_USER_ID = 990201;

    @Autowired
    private IEvaluationBoardService evaluationBoardService;

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    @Autowired
    private InterviewTimeSlotMapper timeSlotMapper;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private SessionInterviewerMapper sessionInterviewerMapper;

    @Test
    @DisplayName("A 周期的面试官对 B 周期不成立，且未绑定任何场次时对两者都不成立")
    void interviewerScopeIsPerCycle() {
        Integer cycleA = null;
        Integer cycleB = null;
        Integer slotA = null;
        Integer sessionA = null;
        Integer bindingId = null;
        try {
            cycleA = insertCycle("测试-隔离校验-A");
            cycleB = insertCycle("测试-隔离校验-B");

            // 尚未绑定任何场次：两个周期都应为 false
            assertFalse(evaluationBoardService.isInterviewerOfCycle(cycleA, INTERVIEWER_USER_ID),
                    "未绑定任何场次时不该被认作面试官");
            assertFalse(evaluationBoardService.isInterviewerOfCycle(cycleB, INTERVIEWER_USER_ID),
                    "未绑定任何场次时不该被认作面试官");

            slotA = insertTimeSlot(cycleA);
            sessionA = insertSession(cycleA, slotA);

            SessionInterviewer binding = new SessionInterviewer();
            binding.setSessionId(sessionA);
            binding.setUserId(INTERVIEWER_USER_ID);
            sessionInterviewerMapper.insert(binding);
            bindingId = binding.getId();
            assertNotNull(bindingId, "插入绑定后应回填主键");

            assertTrue(evaluationBoardService.isInterviewerOfCycle(cycleA, INTERVIEWER_USER_ID),
                    "绑定了 A 周期的场次，对 A 周期应成立");

            assertFalse(evaluationBoardService.isInterviewerOfCycle(cycleB, INTERVIEWER_USER_ID),
                    "只绑定了 A 周期的场次，对 B 周期必须为 false —— 否则改一下文档名里的周期号"
                            + "就能看到别的周期的全部候选人与分数");

            assertFalse(evaluationBoardService.isInterviewerOfCycle(null, INTERVIEWER_USER_ID),
                    "周期为 null 时不该放行");
            assertFalse(evaluationBoardService.isInterviewerOfCycle(cycleA, null),
                    "用户为 null 时不该放行");
        } finally {
            // 逆序清理；周期上的外键是 ON DELETE CASCADE，但显式删更清楚
            if (bindingId != null) {
                sessionInterviewerMapper.deleteById(bindingId);
            }
            if (sessionA != null) {
                sessionMapper.deleteById(sessionA);
            }
            if (slotA != null) {
                timeSlotMapper.deleteById(slotA);
            }
            if (cycleA != null) {
                cycleMapper.deleteById(cycleA);
            }
            if (cycleB != null) {
                cycleMapper.deleteById(cycleB);
            }
        }
    }

    private Integer insertCycle(String name) {
        RecruitmentCycle c = new RecruitmentCycle();
        c.setCycleName(name);
        c.setAcademicYear("2099-2100");
        c.setStartDate(LocalDate.now().minusDays(1));
        c.setEndDate(LocalDate.now().plusDays(1));
        c.setIsActive(0);   // 不设为启用，避免影响「当前开放周期」相关的其它测试
        c.setStatus(1);
        cycleMapper.insert(c);
        assertNotNull(c.getCycleId(), "插入周期后应回填主键");
        return c.getCycleId();
    }

    private Integer insertTimeSlot(Integer cycleId) {
        InterviewTimeSlot s = new InterviewTimeSlot();
        s.setCycleId(cycleId);
        s.setSlotName("测试-时段");
        s.setInterviewDate(LocalDate.now());
        s.setStartTime(LocalTime.of(9, 0));
        s.setEndTime(LocalTime.of(11, 0));
        timeSlotMapper.insert(s);
        assertNotNull(s.getTimeSlotId(), "插入时间窗后应回填主键");
        return s.getTimeSlotId();
    }

    private Integer insertSession(Integer cycleId, Integer timeSlotId) {
        InterviewSession s = new InterviewSession();
        s.setCycleId(cycleId);
        s.setTimeSlotId(timeSlotId);
        s.setDeptId(SEEDED_DEPT_ID);
        s.setLocation("测试-隔离校验");
        s.setCapacity(1);
        sessionMapper.insert(s);
        assertNotNull(s.getSessionId(), "插入场次后应回填主键");
        return s.getSessionId();
    }
}
