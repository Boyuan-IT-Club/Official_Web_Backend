package club.boyuan.official.domain.resume.dto;

import club.boyuan.official.persistence.entity.RecruitmentCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改期申请开关送到用户端的取值。
 * <p>
 * 默认必须是「开」：这个列是 V53 才加的，老周期读出来是 null，
 * 当成「关」会让所有历史周期的改期入口无声消失。
 */
class RescheduleOpenDefaultTest {

    @Test
    @DisplayName("没设过（null）按开处理——老周期不该因为加了列就被关掉")
    void nullMeansOpen() {
        assertTrue(new OpenCycleDTO(cycle(null), 3).isRescheduleOpen());
    }

    @Test
    @DisplayName("1=开，0=关")
    void followsTheFlag() {
        assertTrue(new OpenCycleDTO(cycle(1), 3).isRescheduleOpen());
        assertFalse(new OpenCycleDTO(cycle(0), 3).isRescheduleOpen());
    }

    private static RecruitmentCycle cycle(Integer rescheduleOpen) {
        RecruitmentCycle c = new RecruitmentCycle();
        c.setCycleId(9);
        c.setCycleName("t");
        c.setStartDate(LocalDate.of(2026, 9, 1));
        c.setEndDate(LocalDate.of(2026, 9, 30));
        c.setIsActive(1);
        c.setRescheduleOpen(rescheduleOpen);
        return c;
    }
}
