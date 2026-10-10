package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.persistence.entity.InterviewSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线上面试在评价表里的归属。
 *
 * <p>线上面试不占场次（session_id 为空），于是这一行既没有地点、也没有面试官名单：
 * 面试当天在地点分组里找不着他，而前端 canEdit 要求本人在名单里 —— 空名单等于
 * 这一行谁都填不了评价。
 */
class OnlineInterviewBoardRowTest {

    private static final String ROOM = "文科楼 205";
    private static final int SESSION = 29;
    private static final List<Integer> SESSION_INTERVIEWERS = List.of(7, 8);
    private static final List<Integer> CYCLE_INTERVIEWERS = List.of(7, 8, 9);

    private static final Map<Integer, String> LOCATIONS = Map.of(SESSION, ROOM);
    private static final Map<Integer, List<Integer>> BY_SESSION = Map.of(SESSION, SESSION_INTERVIEWERS);

    private static InterviewSchedule schedule(Integer sessionId, Integer mode) {
        InterviewSchedule s = new InterviewSchedule();
        s.setSessionId(sessionId);
        s.setInterviewMode(mode);
        return s;
    }

    // ---------- 地点 ----------

    @Test
    @DisplayName("线下按场次取教室")
    void offlineTakesRoom() {
        assertEquals(ROOM, EvaluationBoardServiceImpl.locationOf(schedule(SESSION, 0), LOCATIONS));
    }

    @Test
    @DisplayName("线上归到「线上面试」这一组，而不是没有地点")
    void onlineGetsVirtualLocation() {
        assertEquals("线上面试", EvaluationBoardServiceImpl.locationOf(schedule(null, 1), LOCATIONS));
    }

    @Test
    @DisplayName("改线上后场次还残留在行上，也不能把他显示进一间他不会去的教室")
    void onlineWinsOverStaleSession() {
        // 线上 93 号那位就是这样：schedule.session_id 仍是 29，人却不会去 205
        assertEquals("线上面试", EvaluationBoardServiceImpl.locationOf(schedule(SESSION, 1), LOCATIONS));
    }

    @Test
    @DisplayName("既没场次也不是线上（未排期）仍然没有地点")
    void unassignedHasNoLocation() {
        assertNull(EvaluationBoardServiceImpl.locationOf(schedule(null, 0), LOCATIONS));
    }

    // ---------- 面试官名单 ----------

    @Test
    @DisplayName("线下只有排在这一场的面试官能填")
    void offlineKeepsSessionInterviewers() {
        assertEquals(SESSION_INTERVIEWERS,
                EvaluationBoardServiceImpl.interviewersOf(schedule(SESSION, 0), BY_SESSION, CYCLE_INTERVIEWERS));
    }

    @Test
    @DisplayName("线上退到本周期的面试官都能填 —— 否则这一行谁都填不了")
    void onlineFallsBackToCycleInterviewers() {
        assertEquals(CYCLE_INTERVIEWERS,
                EvaluationBoardServiceImpl.interviewersOf(schedule(null, 1), BY_SESSION, CYCLE_INTERVIEWERS));
        // 残留场次同样不该把名单收窄回那一场
        assertEquals(CYCLE_INTERVIEWERS,
                EvaluationBoardServiceImpl.interviewersOf(schedule(SESSION, 1), BY_SESSION, CYCLE_INTERVIEWERS));
    }

    @Test
    @DisplayName("未排期的行名单为空，不借线上那条路放行")
    void unassignedStaysEmpty() {
        assertTrue(EvaluationBoardServiceImpl
                .interviewersOf(schedule(null, 0), BY_SESSION, CYCLE_INTERVIEWERS).isEmpty());
    }
}
