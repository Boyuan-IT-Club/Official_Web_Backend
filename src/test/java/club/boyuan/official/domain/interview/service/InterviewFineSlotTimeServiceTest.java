package club.boyuan.official.domain.interview.service;

import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.domain.interview.service.InterviewFineSlotTimeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterviewFineSlotTimeServiceTest {

    private final InterviewFineSlotTimeService service = new InterviewFineSlotTimeService();

    @Test
    void resolveForOccupiedSlot_dividesWindowEvenly() {
        InterviewSlot slot = new InterviewSlot()
                .setSlotId(1)
                .setInterviewDate(LocalDate.of(2026, 6, 1))
                .setStartTime(LocalTime.of(10, 0))
                .setEndTime(LocalTime.of(12, 0))
                .setMaxCapacity(4)
                .setCurrentOccupied(3);

        LocalDateTime fineTime = service.resolveForOccupiedSlot(slot);

        assertEquals(LocalDateTime.of(2026, 6, 1, 11, 0), fineTime);
    }
}
