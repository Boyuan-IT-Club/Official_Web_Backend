package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.dto.InterviewBookingDTO;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.InterviewSlotMapper;
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
    @Autowired InterviewSlotMapper slots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void mysqlSnapshotPreservesHistoricalLocationAndTimes() {
        RecruitmentCycle cycle = new RecruitmentCycle();
        cycle.setCycleName("历史时段测试");
        cycle.setAcademicYear("2098-2099");
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusDays(1));
        cycles.insert(cycle);
        InterviewSlot slot = new InterviewSlot().setCycleId(cycle.getCycleId())
                .setInterviewDate(LocalDate.of(2024, 9, 10)).setStartTime(LocalTime.of(9, 15))
                .setEndTime(LocalTime.of(10, 5)).setLocation("历史教室").setInterviewType(1)
                .setMaxCapacity(4).setCurrentOccupied(2).setStatus(1);
        slots.insert(slot);
        String snapshot = jdbc.queryForObject("SELECT snapshot FROM legacy_interview_slot_source WHERE slot_id=?",
                String.class, slot.getSlotId());
        InterviewSchedule schedule = new InterviewSchedule().setLegacySlotSnapshot(snapshot)
                .setInterviewTime(slot.getInterviewDate().atTime(9, 25));
        InterviewSlot restored = schedule.historicalSlot();
        var dto = InterviewBookingDTO.from(schedule, restored);
        assertEquals(slot.getSlotId(), dto.getSlotId());
        assertEquals(slot.getLocation(), dto.getLocation());
        assertEquals(slot.getStartTime(), dto.getStartTime());
        assertEquals(slot.getInterviewDate(), dto.getInterviewDate());
        assertEquals(schedule.getInterviewTime(), dto.getInterviewTime());
        assertNotNull(restored.getCreatedAt());
    }
}
