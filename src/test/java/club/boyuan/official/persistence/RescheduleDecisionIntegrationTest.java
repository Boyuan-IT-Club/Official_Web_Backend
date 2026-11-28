package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.RescheduleDecisionService;
import club.boyuan.official.persistence.entity.InterviewRescheduleRequest;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.InterviewRescheduleRequestMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.InterviewTimeSlotMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class RescheduleDecisionIntegrationTest {
    @Autowired RescheduleDecisionService decisions;
    @Autowired InterviewRescheduleRequestMapper requests;
    @Autowired InterviewScheduleMapper schedules;
    @SpyBean InterviewSessionMapper sessions;
    @Autowired InterviewTimeSlotMapper slots;
    @Autowired RecruitmentCycleMapper cycles;
    @Autowired ResumeMapper resumes;
    @Autowired UserMapper users;

    private final RecruitmentCycle cycle = new RecruitmentCycle();
    private final Resume resume = new Resume();
    private final InterviewSchedule schedule = new InterviewSchedule();
    private final InterviewSession session = new InterviewSession();
    private final InterviewRescheduleRequest request = new InterviewRescheduleRequest();
    private String handler;

    @BeforeEach
    void arrange() {
        var user = users.selectById(1);
        handler = user.getUsername();
        cycle.setCycleName("审批事务回归");
        cycle.setAcademicYear("2099-2100");
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusDays(1));
        cycle.setIsActive(0);
        cycles.insert(cycle);
        var slot = new InterviewTimeSlot().setCycleId(cycle.getCycleId()).setSlotName("审批测试")
                .setInterviewDate(LocalDate.now()).setStartTime(LocalTime.of(9, 0)).setEndTime(LocalTime.NOON);
        slots.insert(slot);
        sessions.insert(session.setCycleId(cycle.getCycleId()).setTimeSlotId(slot.getTimeSlotId())
                .setDeptIds(List.of(1)).setLocation("测试").setCurrentOccupied(1).setCapacity(2));
        resume.setUserId(user.getUserId());
        resume.setCycleId(cycle.getCycleId());
        resume.setStatus(2);
        resumes.insert(resume);
        schedules.insert(schedule.setCycleId(cycle.getCycleId()).setResumeId(resume.getResumeId())
                .setUserId(user.getUserId()).setSessionId(session.getSessionId()).setStatus(1).setNotifStatus(1));
        requests.insert(request.setCycleId(cycle.getCycleId()).setScheduleId(schedule.getScheduleId())
                .setResumeId(resume.getResumeId()).setUserId(user.getUserId()).setReason("改期").setStatus(0));
    }

    @AfterEach
    void clean() {
        if (request.getRequestId() != null) requests.deleteById(request.getRequestId());
        if (schedule.getScheduleId() != null) schedules.deleteById(schedule.getScheduleId());
        if (resume.getResumeId() != null) resumes.deleteById(resume.getResumeId());
        if (cycle.getCycleId() != null) cycles.deleteById(cycle.getCycleId());
    }

    @Test
    void approvalAndRejectionCanOnlyClaimTheRequestOnce() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var approve = workers.submit(contender(1, ready, start));
            var reject = workers.submit(contender(2, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean approved = approve.get(15, TimeUnit.SECONDS);
            assertNotEquals(approved, reject.get(15, TimeUnit.SECONDS));
            assertEquals(approved ? 1 : 2, requests.selectById(request.getRequestId()).getStatus());
            assertEquals(approved ? 2 : 1, schedules.selectById(schedule.getScheduleId()).getStatus());
            assertEquals(approved ? 0 : 1, sessions.selectById(session.getSessionId()).getCurrentOccupied());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private Callable<Boolean> contender(int status, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                decisions.handle(request.getRequestId(), status, "处理", handler);
                return true;
            } catch (RescheduleDecisionService.InvalidDecision e) {
                assertEquals("该申请已处理", e.getMessage());
                return false;
            }
        };
    }

    @Test
    void cancelledScheduleCanGoOnlineWithoutReturningAnotherSeat() {
        schedules.updateById(schedule.setStatus(2));
        requests.updateById(request.setRequestType(InterviewRescheduleRequest.TYPE_TO_ONLINE));
        decisions.handle(request.getRequestId(), 1, "线上", handler);
        var row = schedules.selectById(schedule.getScheduleId());
        assertEquals(1, row.getInterviewMode());
        assertEquals(1, row.getStatus());
        assertEquals(0, row.getNotifStatus());
        assertNull(row.getSessionId());
        assertEquals(1, sessions.selectById(session.getSessionId()).getCurrentOccupied());
    }

    @Test
    void failureReturningTheSeatRollsBackPriorUpdates() {
        doThrow(new IllegalStateException("seat update failed")).when(sessions).releaseOne(session.getSessionId());
        assertThrows(IllegalStateException.class, () -> decisions.handle(request.getRequestId(), 1, "改期", handler));
        assertEquals(0, requests.selectById(request.getRequestId()).getStatus());
        assertNull(requests.selectById(request.getRequestId()).getHandledBy());
        assertEquals(1, schedules.selectById(schedule.getScheduleId()).getStatus());
        assertEquals(1, sessions.selectById(session.getSessionId()).getCurrentOccupied());
    }
}
