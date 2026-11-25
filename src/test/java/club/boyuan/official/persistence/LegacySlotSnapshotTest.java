package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.dto.InterviewBookingDTO;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LegacySlotSnapshotTest {
    @Autowired RecruitmentCycleMapper cycles;
    @Autowired InterviewScheduleMapper schedules;
    @Autowired JdbcTemplate jdbc;

    @Test
    void mysqlSnapshotPreservesHistoricalLocationAndTimes() {
        RecruitmentCycle cycle = new RecruitmentCycle();
        cycle.setCycleName("历史时段测试");
        cycle.setAcademicYear("2098-2099");
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusDays(1));
        cycles.insert(cycle);
        String snapshot = """
                {"slotId":45,"cycleId":1,"location":"历史教室","interviewDate":"2024-09-10",
                 "startTime":"09:15:00","endTime":"10:05:00","interviewType":1,"maxCapacity":4,
                 "currentOccupied":2,"status":1,"createdAt":"2024-09-01 10:00:00","updatedAt":"2024-09-02 10:00:00"}
                """;
        var userId = jdbc.queryForObject("SELECT MIN(user_id) FROM user", Integer.class);
        jdbc.update("INSERT INTO resume(user_id,cycle_id) VALUES(?,?)",userId,cycle.getCycleId());
        var resumeId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        var input = new InterviewSchedule().setCycleId(cycle.getCycleId()).setUserId(userId).setResumeId(resumeId)
                .setLegacySlotSnapshot(snapshot).setInterviewTime(LocalDate.of(2024,9,10).atTime(9,25));
        schedules.insert(input);
        jdbc.update("UPDATE interview_schedule SET legacy_slot_snapshot=? WHERE schedule_id=?",snapshot,input.getScheduleId());
        InterviewSchedule schedule = schedules.selectById(input.getScheduleId());
        InterviewSlot restored = schedule.historicalSlot();
        var dto = InterviewBookingDTO.from(schedule, restored);
        assertEquals(45, dto.getSlotId());
        assertEquals("历史教室", dto.getLocation());
        assertEquals(LocalTime.of(9,15), dto.getStartTime());
        assertEquals(LocalDate.of(2024,9,10), dto.getInterviewDate());
        assertEquals(schedule.getInterviewTime(), dto.getInterviewTime());
        assertNotNull(restored.getCreatedAt());
    }
}
