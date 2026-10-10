package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面试官能看谁的附件。
 *
 * <p>这是个安全边界：放宽一点，任意面试官就能顺着连续的附件 id 把全部候选人的
 * 作品集、成绩单拖走；收紧一点，面试官面试时看不到自己要面那位的材料。
 */
class InterviewerMaterialScopeTest {

    private static final int CANDIDATE = 50;
    private static final int CYCLE = 14;
    private static final int SESSION = 29;

    /** 排在 CANDIDATE 那一场上的面试官 */
    private static final int BOUND = 7;
    /** 本周期在面试，但没排到 CANDIDATE 那一场 */
    private static final int ELSEWHERE = 9;
    /** 完全不相干的人 */
    private static final int OUTSIDER = 99;

    private InterviewScheduleMapper scheduleMapper;
    private SessionInterviewerMapper sessionInterviewerMapper;
    private IEvaluationBoardService boardService;
    private InterviewerMaterialScope scope;

    @BeforeEach
    void setUp() {
        scheduleMapper = mock(InterviewScheduleMapper.class);
        sessionInterviewerMapper = mock(SessionInterviewerMapper.class);
        boardService = mock(IEvaluationBoardService.class);
        scope = new InterviewerMaterialScope(scheduleMapper, sessionInterviewerMapper, boardService);
    }

    private static InterviewSchedule schedule(Integer sessionId, Integer mode) {
        InterviewSchedule s = new InterviewSchedule();
        s.setUserId(CANDIDATE);
        s.setCycleId(CYCLE);
        s.setSessionId(sessionId);
        s.setInterviewMode(mode);
        return s;
    }

    @SuppressWarnings("unchecked")
    private void candidateHas(InterviewSchedule... schedules) {
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(schedules));
    }

    @SuppressWarnings("unchecked")
    private void boundToSession(boolean bound) {
        when(sessionInterviewerMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(bound);
    }

    // ---------- 线下：按场次绑定 ----------

    @Test
    @DisplayName("排在这一场的面试官看得到")
    void boundInterviewerCanSee() {
        candidateHas(schedule(SESSION, 0));
        boundToSession(true);
        assertTrue(scope.canSeeCandidateMaterials(BOUND, CANDIDATE));
    }

    @Test
    @DisplayName("没排到这一场的看不到 —— 不许顺着 id 翻全库")
    void unboundInterviewerCannotSee() {
        candidateHas(schedule(SESSION, 0));
        boundToSession(false);
        assertFalse(scope.canSeeCandidateMaterials(ELSEWHERE, CANDIDATE));
    }

    @Test
    @DisplayName("线下的人不去问「本周期面试官」那条路，免得绕开场次绑定")
    void offlineNeverFallsBackToCycle() {
        candidateHas(schedule(SESSION, 0));
        boundToSession(false);
        scope.canSeeCandidateMaterials(ELSEWHERE, CANDIDATE);
        verify(boardService, never()).isInterviewerOfCycle(any(), any());
    }

    // ---------- 线上：没有场次可绑 ----------

    @Test
    @DisplayName("线上面试：本周期的面试官看得到 —— 否则面试当天材料全看不了")
    void onlineVisibleToCycleInterviewer() {
        candidateHas(schedule(null, 1));
        boundToSession(false);
        when(boardService.isInterviewerOfCycle(CYCLE, ELSEWHERE)).thenReturn(true);
        assertTrue(scope.canSeeCandidateMaterials(ELSEWHERE, CANDIDATE));
    }

    @Test
    @DisplayName("线上面试：不是本周期面试官的仍然看不到")
    void onlineDeniedForOutsider() {
        candidateHas(schedule(null, 1));
        boundToSession(false);
        when(boardService.isInterviewerOfCycle(CYCLE, OUTSIDER)).thenReturn(false);
        assertFalse(scope.canSeeCandidateMaterials(OUTSIDER, CANDIDATE));
    }

    @Test
    @DisplayName("改线上后场次仍残留：按线上那条路放行，不受残留场次影响")
    void onlineWithStaleSessionStillUsesCyclePath() {
        candidateHas(schedule(SESSION, 1));
        boundToSession(false);
        when(boardService.isInterviewerOfCycle(CYCLE, ELSEWHERE)).thenReturn(true);
        assertTrue(scope.canSeeCandidateMaterials(ELSEWHERE, CANDIDATE));
    }

    // ---------- 边界 ----------

    @Test
    @DisplayName("没有任何面试安排的候选人，谁都看不到")
    void noScheduleMeansNoAccess() {
        candidateHas();
        assertFalse(scope.canSeeCandidateMaterials(BOUND, CANDIDATE));
    }

    @Test
    @DisplayName("用户号缺失时直接拒绝，不去查库")
    void nullsRejectedWithoutQuery() {
        assertFalse(scope.canSeeCandidateMaterials(null, CANDIDATE));
        assertFalse(scope.canSeeCandidateMaterials(BOUND, null));
        verify(scheduleMapper, never()).selectList(any());
    }
}
