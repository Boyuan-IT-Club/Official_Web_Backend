package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归保护:面试官的评价表准入必须按招募周期隔离。
 *
 * 修复前的漏洞:协同服务的 onAuthenticate 只校验「令牌里有没有 interview:evaluate」,
 * 不校验「这个人是不是这个周期的面试官」。文档名形如 eval-board:{cycleId},
 * 于是 A 周期的面试官把地址里的周期号一改就能打开 B 周期的评价表,
 * 看到 B 周期的全部候选人名单与分数。
 *
 * 本测试锁住判定本身:绑定在 A 周期场次上的面试官,对 B 周期必须返回 false。
 */
@SpringBootTest
class CycleInterviewerScopeIntegrationTest {

    /** 与真实数据错开,避免和生产/开发库里的周期撞号 */
    private static final int CYCLE_A = 990101;
    private static final int CYCLE_B = 990102;
    private static final int INTERVIEWER_USER_ID = 990201;

    @Autowired
    private IEvaluationBoardService evaluationBoardService;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private SessionInterviewerMapper sessionInterviewerMapper;

    @Test
    @DisplayName("A 周期的面试官对 B 周期不成立，且未绑定任何场次时对两者都不成立")
    void interviewerScopeIsPerCycle() {
        Integer sessionA = null;
        Integer bindingId = null;
        try {
            // 尚未绑定任何场次：两个周期都应为 false
            assertFalse(evaluationBoardService.isInterviewerOfCycle(CYCLE_A, INTERVIEWER_USER_ID),
                    "未绑定任何场次时不该被认作面试官");

            sessionA = insertSession(CYCLE_A);

            SessionInterviewer binding = new SessionInterviewer();
            binding.setSessionId(sessionA);
            binding.setUserId(INTERVIEWER_USER_ID);
            sessionInterviewerMapper.insert(binding);
            bindingId = binding.getId();
            assertNotNull(bindingId, "插入绑定后应回填主键");

            assertTrue(evaluationBoardService.isInterviewerOfCycle(CYCLE_A, INTERVIEWER_USER_ID),
                    "绑定了 A 周期场次，对 A 周期应成立");

            assertFalse(evaluationBoardService.isInterviewerOfCycle(CYCLE_B, INTERVIEWER_USER_ID),
                    "只绑定了 A 周期的场次，对 B 周期必须为 false —— 否则改一下地址栏的周期号"
                            + "就能看到别的周期全部候选人");

            assertFalse(evaluationBoardService.isInterviewerOfCycle(null, INTERVIEWER_USER_ID),
                    "周期为 null 时不该放行");
            assertFalse(evaluationBoardService.isInterviewerOfCycle(CYCLE_A, null),
                    "用户为 null 时不该放行");
        } finally {
            if (bindingId != null) {
                sessionInterviewerMapper.deleteById(bindingId);
            }
            if (sessionA != null) {
                interviewSessionMapper.deleteById(sessionA);
            }
        }
    }

    private Integer insertSession(int cycleId) {
        InterviewSession s = new InterviewSession();
        s.setCycleId(cycleId);
        s.setTimeSlotId(0);
        s.setDeptId(0);
        s.setLocation("测试-隔离校验");
        s.setCapacity(1);
        interviewSessionMapper.insert(s);
        assertNotNull(s.getSessionId(), "插入场次后应回填主键");
        return s.getSessionId();
    }
}
