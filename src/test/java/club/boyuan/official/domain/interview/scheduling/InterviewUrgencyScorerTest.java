package club.boyuan.official.domain.interview.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewUrgencyScorerTest {

    private final LocalDate day1 = LocalDate.of(2025, 9, 27);

    private Map<String, Map<LocalDateTime, Boolean>> availabilityWith(String dept, LocalTime time, boolean available) {
        Map<LocalDateTime, Boolean> slots = new HashMap<>();
        slots.put(LocalDateTime.of(day1, time), available);
        Map<String, Map<LocalDateTime, Boolean>> map = new HashMap<>();
        map.put(dept, slots);
        return map;
    }

    @Test
    void getAvailableSlotsCount_countsOnlyMatchingAndAvailable() {
        Map<LocalDateTime, Boolean> slots = new HashMap<>();
        slots.put(LocalDateTime.of(day1, LocalTime.of(9, 0)), true);   // 上午, 可用
        slots.put(LocalDateTime.of(day1, LocalTime.of(10, 0)), true);  // 上午, 可用
        slots.put(LocalDateTime.of(day1, LocalTime.of(14, 0)), true);  // 下午, 可用 -> 不匹配"上午"
        slots.put(LocalDateTime.of(day1, LocalTime.of(11, 0)), false); // 上午, 不可用
        Map<String, Map<LocalDateTime, Boolean>> map = new HashMap<>();
        map.put("技术部", slots);

        int count = InterviewUrgencyScorer.getAvailableSlotsCount("Day 1 上午", "技术部", map);

        assertEquals(2, count);
    }

    @Test
    void getAvailableSlotsCount_unknownDepartmentReturnsZero() {
        Map<String, Map<LocalDateTime, Boolean>> map = availabilityWith("技术部", LocalTime.of(9, 0), true);
        assertEquals(0, InterviewUrgencyScorer.getAvailableSlotsCount("Day 1 上午", "运营部", map));
    }

    @Test
    void urgencyScore_fewerPreferencesIsMoreUrgent() {
        Map<String, Integer> demand = Map.of("Day 1 上午", 1, "Day 1 下午", 1);
        // 两个部门都各有一个匹配的可用槽，稀缺性一致；比较基础紧迫度
        Map<String, Map<LocalDateTime, Boolean>> avail = availabilityWith("技术部", LocalTime.of(9, 0), true);
        avail.get("技术部").put(LocalDateTime.of(day1, LocalTime.of(14, 0)), true);

        double oneChoice = InterviewUrgencyScorer.calculateUrgencyScore(
                List.of("Day 1 上午"), "技术部", demand, avail);
        double twoChoices = InterviewUrgencyScorer.calculateUrgencyScore(
                List.of("Day 1 上午", "Day 1 下午"), "技术部", demand, avail);

        assertTrue(oneChoice > twoChoices, "偏好越少应越紧迫");
    }

    @Test
    void urgencyScore_noAvailableSlotUsesMaxScarcityWeight() {
        Map<String, Integer> demand = Map.of("Day 1 上午", 5);
        // 该部门无任何可用槽 -> 稀缺性加权取最大值 10.0
        Map<String, Map<LocalDateTime, Boolean>> avail = availabilityWith("技术部", LocalTime.of(9, 0), false);

        double score = InterviewUrgencyScorer.calculateUrgencyScore(
                List.of("Day 1 上午"), "技术部", demand, avail);

        // baseUrgency=1.0*0.6 ; scarcity=10.0*0.4=4.0 => 4.6
        assertEquals(0.6 + InterviewUrgencyScorer.NO_SLOT_SCARCITY_WEIGHT * 0.4, score, 1e-9);
    }

    @Test
    void urgencyScore_higherCompetitionIsMoreUrgent() {
        // 同为1个可用槽，需求越高 -> 竞争越激烈 -> 分数越高
        Map<String, Map<LocalDateTime, Boolean>> avail = availabilityWith("技术部", LocalTime.of(9, 0), true);

        double lowDemand = InterviewUrgencyScorer.calculateUrgencyScore(
                List.of("Day 1 上午"), "技术部", Map.of("Day 1 上午", 2), avail);
        double highDemand = InterviewUrgencyScorer.calculateUrgencyScore(
                List.of("Day 1 上午"), "技术部", Map.of("Day 1 上午", 20), avail);

        assertTrue(highDemand > lowDemand, "竞争越激烈应越紧迫");
    }
}
