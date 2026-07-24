package club.boyuan.official.domain.interview.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InterviewSlotTimeCalculator} 的纯算法单测。
 * 覆盖时间槽生成、时段判定与偏好匹配的边界。
 */
class InterviewSlotTimeCalculatorTest {

    private static final LocalDate DAY1 = InterviewSlotTimeCalculator.DAY_ONE;

    @Test
    void singleDay_generatesExpectedSlotCounts() {
        List<LocalDateTime> slots = InterviewSlotTimeCalculator.generateTimeSlotsForSingleDay(DAY1);

        // 上午 09:00-11:00 => 12 段；下午 13:00-17:30 => 27 段；晚上 19:00-21:00 => 12 段
        long morning = slots.stream().filter(s -> "上午".equals(InterviewSlotTimeCalculator.periodOf(s.toLocalTime()))).count();
        long afternoon = slots.stream().filter(s -> "下午".equals(InterviewSlotTimeCalculator.periodOf(s.toLocalTime()))).count();
        long evening = slots.stream().filter(s -> "晚上".equals(InterviewSlotTimeCalculator.periodOf(s.toLocalTime()))).count();

        assertEquals(12, morning);
        assertEquals(27, afternoon);
        assertEquals(12, evening);
        assertEquals(51, slots.size());
    }

    @Test
    void singleDay_boundariesAreHalfOpen() {
        List<LocalDateTime> slots = InterviewSlotTimeCalculator.generateTimeSlotsForSingleDay(DAY1);

        assertTrue(slots.contains(LocalDateTime.of(DAY1, LocalTime.of(9, 0))));
        // 上午结束点 11:00 不包含（半开区间）
        assertFalse(slots.contains(LocalDateTime.of(DAY1, LocalTime.of(11, 0))));
        // 上午最后一段是 10:50
        assertTrue(slots.contains(LocalDateTime.of(DAY1, LocalTime.of(10, 50))));
        // 下午最后一段 17:20，17:30 不含
        assertTrue(slots.contains(LocalDateTime.of(DAY1, LocalTime.of(17, 20))));
        assertFalse(slots.contains(LocalDateTime.of(DAY1, LocalTime.of(17, 30))));
    }

    @Test
    void generateTimeSlots_coversTwoDaysMorningAndAfternoonOnly() {
        LocalDate start = LocalDate.of(2025, 9, 27);
        List<LocalDateTime> slots = InterviewSlotTimeCalculator.generateTimeSlots(start, start.plusDays(5));

        // 仅前两天，且只有上午/下午（无晚上）
        assertTrue(slots.stream().allMatch(s ->
                s.toLocalDate().equals(start) || s.toLocalDate().equals(start.plusDays(1))));
        assertTrue(slots.stream().noneMatch(s -> "晚上".equals(InterviewSlotTimeCalculator.periodOf(s.toLocalTime()))));
        // 每天 12(上午) + 27(下午) = 39，两天 78
        assertEquals(78, slots.size());
    }

    @Test
    void periodOf_classifiesTimesCorrectly() {
        assertEquals("上午", InterviewSlotTimeCalculator.periodOf(LocalTime.of(9, 0)));
        assertEquals("上午", InterviewSlotTimeCalculator.periodOf(LocalTime.of(10, 59)));
        assertEquals("下午", InterviewSlotTimeCalculator.periodOf(LocalTime.of(13, 0)));
        assertEquals("下午", InterviewSlotTimeCalculator.periodOf(LocalTime.of(17, 29)));
        assertEquals("晚上", InterviewSlotTimeCalculator.periodOf(LocalTime.of(19, 0)));
        assertEquals("晚上", InterviewSlotTimeCalculator.periodOf(LocalTime.of(20, 59)));
    }

    @Test
    void periodOf_returnsUnknownForGaps() {
        assertEquals("未知", InterviewSlotTimeCalculator.periodOf(LocalTime.of(8, 59)));
        assertEquals("未知", InterviewSlotTimeCalculator.periodOf(LocalTime.of(11, 0)));
        assertEquals("未知", InterviewSlotTimeCalculator.periodOf(LocalTime.of(12, 30)));
        assertEquals("未知", InterviewSlotTimeCalculator.periodOf(LocalTime.of(18, 0)));
        assertEquals("未知", InterviewSlotTimeCalculator.periodOf(LocalTime.of(21, 0)));
    }

    @Test
    void isSlotMatchPreference_matchesCorrectDayAndPeriod() {
        LocalDateTime morningSlot = LocalDateTime.of(DAY1, LocalTime.of(9, 30));
        assertTrue(InterviewSlotTimeCalculator.isSlotMatchPreference(morningSlot, "Day 1 上午"));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(morningSlot, "Day 1 下午"));

        LocalDateTime afternoonSlot = LocalDateTime.of(DAY1, LocalTime.of(14, 0));
        assertTrue(InterviewSlotTimeCalculator.isSlotMatchPreference(afternoonSlot, "Day 1 下午"));

        LocalDateTime eveningSlot = LocalDateTime.of(DAY1, LocalTime.of(20, 0));
        assertTrue(InterviewSlotTimeCalculator.isSlotMatchPreference(eveningSlot, "Day 1 晚上"));
    }

    @Test
    void isSlotMatchPreference_rejectsWrongDateOrDayNumber() {
        LocalDateTime otherDay = LocalDateTime.of(DAY1.plusDays(1), LocalTime.of(9, 30));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(otherDay, "Day 1 上午"));

        LocalDateTime day1Slot = LocalDateTime.of(DAY1, LocalTime.of(9, 30));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(day1Slot, "Day 2 上午"));
    }

    @Test
    void calculateTimeSlotDemand_countsPreferencesAcrossUsers() {
        Map<Integer, List<String>> prefs = Map.of(
                1, List.of("Day 1 上午", "Day 1 下午"),
                2, List.of("Day 1 上午"),
                3, List.of("Day 1 晚上", "Day 1 上午"));

        Map<String, Integer> demand = InterviewSlotTimeCalculator.calculateTimeSlotDemand(prefs);

        assertEquals(3, demand.get("Day 1 上午"));
        assertEquals(1, demand.get("Day 1 下午"));
        assertEquals(1, demand.get("Day 1 晚上"));
    }

    @Test
    void calculateTimeSlotDemand_emptyInputYieldsEmpty() {
        assertTrue(InterviewSlotTimeCalculator.calculateTimeSlotDemand(Map.of()).isEmpty());
    }

    @Test
    void isSlotMatchPreference_rejectsMalformedInput() {
        LocalDateTime slot = LocalDateTime.of(DAY1, LocalTime.of(9, 30));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(slot, null));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(slot, ""));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(slot, "上午"));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(slot, "Day X 上午"));
        assertFalse(InterviewSlotTimeCalculator.isSlotMatchPreference(slot, "Week 1 上午"));
    }
}
