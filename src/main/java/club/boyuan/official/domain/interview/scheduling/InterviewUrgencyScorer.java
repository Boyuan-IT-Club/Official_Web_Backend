package club.boyuan.official.domain.interview.scheduling;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试分配的候选人紧迫度评分（纯函数，无 DB/状态依赖）。
 * <p>
 * 从 {@code InterviewAssignmentServiceImpl} 抽出，行为与原实现保持一致：
 * 综合「基础紧迫度（偏好越少越紧迫）」与「稀缺性加权（竞争越激烈越紧迫）」。
 */
public final class InterviewUrgencyScorer {

    /** 某时间段已无可用时间槽时给予的最高稀缺性加权。 */
    static final double NO_SLOT_SCARCITY_WEIGHT = 10.0;

    private InterviewUrgencyScorer() {
    }

    /**
     * 计算候选人紧迫度分数：分数越高越应优先分配。
     *
     * @param preferredTimes             候选人偏好时间段列表（如 "Day 1 上午"）
     * @param firstDepartment            候选人首选部门
     * @param timeSlotDemand             各时间段的需求人数
     * @param departmentSlotAvailability 各部门时间槽可用性映射
     * @return 紧迫度分数（60% 基础紧迫度 + 40% 稀缺性加权）
     */
    public static double calculateUrgencyScore(List<String> preferredTimes,
                                               String firstDepartment,
                                               Map<String, Integer> timeSlotDemand,
                                               Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        double baseUrgency = 1.0 / preferredTimes.size();

        double scarcityWeight = 0.0;
        for (String timeSlot : preferredTimes) {
            int demand = timeSlotDemand.getOrDefault(timeSlot, 0);
            int availableSlots = getAvailableSlotsCount(timeSlot, firstDepartment, departmentSlotAvailability);
            if (availableSlots > 0) {
                scarcityWeight += (double) demand / availableSlots;
            } else {
                scarcityWeight += NO_SLOT_SCARCITY_WEIGHT;
            }
        }
        scarcityWeight = scarcityWeight / preferredTimes.size();

        return baseUrgency * 0.6 + scarcityWeight * 0.4;
    }

    /**
     * 统计指定部门中符合期望时间段且仍可用的时间槽数量。
     */
    public static int getAvailableSlotsCount(String preferredTime,
                                             String department,
                                             Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<LocalDateTime, Boolean> entry : slotAvailability.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())
                    && InterviewSlotTimeCalculator.isSlotMatchPreference(entry.getKey(), preferredTime)) {
                count++;
            }
        }
        return count;
    }
}
