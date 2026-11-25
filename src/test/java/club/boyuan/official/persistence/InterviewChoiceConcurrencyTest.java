package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.dto.CreateInterviewSessionRequestDTO;
import club.boyuan.official.domain.interview.dto.SubmitInterviewPreferenceRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.interview.service.ISessionAssignmentService;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewTimeSlotMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InterviewChoiceConcurrencyTest {
    @Autowired RecruitmentCycleMapper cycles;
    @Autowired InterviewTimeSlotMapper windows;
    @Autowired UserMapper users;
    @Autowired ResumeMapper resumes;
    @Autowired IInterviewSessionService sessions;
    @Autowired IInterviewTimeSlotService windowService;
    @Autowired IInterviewPreferenceService preferences;
    @Autowired ISessionAssignmentService assignments;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentAllocationSharesSeatsAndSecondaryDepartment() throws Exception {
        var cycle = new RecruitmentCycle();
        cycle.setCycleName("并发分配测试");
        cycle.setAcademicYear("2098-2099");
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusDays(2));
        cycle.setIsActive(0);
        cycles.insert(cycle);
        int cycleId = cycle.getCycleId();
        List<Integer> userIds = new ArrayList<>();
        var workers = Executors.newFixedThreadPool(2);
        try {
            int slot = window(cycleId, "场次时间");
            int spare = window(cycleId, "可删除时间");
            var request = new CreateInterviewSessionRequestDTO();
            request.setCycleId(cycleId);
            request.setDeptIds(List.of(1, 2));
            request.setTimeSlotId(slot);
            request.setCapacity(1);
            request.setLocation("测试教室");
            int session = sessions.createSession(request).getSessionId();
            assertTrue(sessions.listSessionDTOs(cycleId, 2, true).stream().anyMatch(x -> x.getSessionId() == session));
            for (int i = 0; i < 2; i++) {
                var user = new User();
                String name = "choice_" + System.nanoTime();
                user.setUsername(name);
                user.setEmail(name + "@example.invalid");
                user.setPassword("test");
                users.insert(user);
                userIds.add(user.getUserId());
                var resume = new Resume();
                resume.setUserId(user.getUserId());
                resume.setCycleId(cycleId);
                resume.setStatus(2);
                resumes.insert(resume);
                var pref = new SubmitInterviewPreferenceRequestDTO();
                pref.setCycleId(cycleId);
                pref.setFirstDeptId(2);
                pref.setTimeSlotIds(List.of(spare, slot));
                preferences.submitPreference(user.getUserId(), pref);
            }
            windowService.deleteTimeSlot(spare);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM interview_preference WHERE cycle_id=? AND accepted_time_slot_ids=JSON_ARRAY(?)", Integer.class, cycleId, slot));
            var start = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> allocate = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return assignments.assign(cycleId).getAssignedCount();
            };
            var first = workers.submit(allocate);
            var second = workers.submit(allocate);
            start.countDown();
            assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT current_occupied FROM interview_session WHERE session_id=?", Integer.class, session));
            jdbc.update("UPDATE interview_session SET capacity=2 WHERE session_id=?", session);
            assertEquals(1, assignments.assign(cycleId).getAssignedCount());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT interview_time) FROM interview_schedule WHERE session_id=? AND dept_id=2", Integer.class, session));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            jdbc.update("DELETE FROM interview_schedule WHERE cycle_id=?", cycleId);
            jdbc.update("DELETE d FROM interview_session_dept d JOIN interview_session s USING(session_id) WHERE s.cycle_id=?", cycleId);
            jdbc.update("DELETE FROM resume WHERE cycle_id=?", cycleId);
            cycles.deleteById(cycleId);
            userIds.forEach(users::deleteById);
        }
    }

    private int window(int cycleId, String name) {
        var slot = new InterviewTimeSlot().setCycleId(cycleId).setSlotName(name).setInterviewDate(LocalDate.now())
                .setStartTime(LocalTime.of(9, 0)).setEndTime(LocalTime.NOON).setStatus(1);
        windows.insert(slot);
        return slot.getTimeSlotId();
    }
}
