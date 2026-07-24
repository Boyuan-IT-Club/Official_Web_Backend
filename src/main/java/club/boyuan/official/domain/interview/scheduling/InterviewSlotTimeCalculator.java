package club.boyuan.official.domain.interview.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试时间片纯算法：时间槽生成、时段（上午/下午/晚上）判定、用户偏好时间匹配。
 * <p>
 * 从 {@code InterviewAssignmentServiceImpl} 抽出，行为保持一致。全部为纯函数、无副作用、无依赖，
 * 便于单元测试，也让分配服务专注于编排逻辑。
 */
public final class InterviewSlotTimeCalculator {

    public static final LocalTime MORNING_START = LocalTime.of(9, 0);
    public static final LocalTime MORNING_END = LocalTime.of(11, 0);
    public static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    public static final LocalTime AFTERNOON_END = LocalTime.of(17, 30);
    public static final LocalTime EVENING_START = LocalTime.of(19, 0);
    public static final LocalTime EVENING_END = LocalTime.of(21, 0);

    /** 单场面试时长（分钟） */
    public static final int INTERVIEW_DURATION_MINUTES = 10;

    /** 目前仅支持 "Day 1"，对应 2025-09-27 */
    public static final LocalDate DAY_ONE = LocalDate.of(2025, 9, 27);

    private InterviewSlotTimeCalculator() {
    }

    /**
     * 为单个日期生成面试时间槽（上午、下午、晚上）。
     */
    public static List<LocalDateTime> generateTimeSlotsForSingleDay(LocalDate day) {
        List<LocalDateTime> timeSlots = new ArrayList<>();
        addSlots(timeSlots, day, MORNING_START, MORNING_END);
        addSlots(timeSlots, day, AFTERNOON_START, AFTERNOON_END);
        addSlots(timeSlots, day, EVENING_START, EVENING_END);
        return timeSlots;
    }

    /**
     * 基于招募周期起始日，为前两天生成面试时间槽（仅上午、下午）。
     */
    public static List<LocalDateTime> generateTimeSlots(LocalDate startDate, LocalDate endDate) {
        List<LocalDateTime> timeSlots = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < 2; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            addSlots(timeSlots, date, MORNING_START, MORNING_END);
            addSlots(timeSlots, date, AFTERNOON_START, AFTERNOON_END);
        }
        return timeSlots;
    }

    /**
     * 判定某个时间槽属于哪个时段。
     *
     * @return "上午" / "下午" / "晚上"，均不匹配时返回 "未知"
     */
    public static String periodOf(LocalTime timeOfDay) {
        if (!timeOfDay.isBefore(MORNING_START) && timeOfDay.isBefore(MORNING_END)) {
            return "上午";
        }
        if (!timeOfDay.isBefore(AFTERNOON_START) && timeOfDay.isBefore(AFTERNOON_END)) {
            return "下午";
        }
        if (!timeOfDay.isBefore(EVENING_START) && timeOfDay.isBefore(EVENING_END)) {
            return "晚上";
        }
        return "未知";
    }

    /**
     * 检查时间槽是否符合用户期望时间（格式如 "Day 1 上午"）。严格按日期 + 时段匹配。
     */
    public static boolean isSlotMatchPreference(LocalDateTime slotTime, String preferredTime) {
        if (preferredTime == null) {
            return false;
        }
        String[] parts = preferredTime.split(" ");
        if (parts.length < 3 || !"Day".equals(parts[0])) {
            return false;
        }
        try {
            int dayNumber = Integer.parseInt(parts[1]);
            String period = parts[2];
            if (dayNumber == 1 && slotTime.toLocalDate().equals(DAY_ONE)) {
                LocalTime time = slotTime.toLocalTime();
                return period.equals(periodOf(time));
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    /**
     * 统计每个偏好时间段被多少人选择（需求人数）。
     *
     * @param userPreferredTimes 用户ID -> 其偏好时间段列表
     * @return 时间段 -> 需求人数
     */
    public static Map<String, Integer> calculateTimeSlotDemand(Map<Integer, List<String>> userPreferredTimes) {
        Map<String, Integer> demand = new HashMap<>();
        for (List<String> preferredTimes : userPreferredTimes.values()) {
            for (String timeSlot : preferredTimes) {
                demand.merge(timeSlot, 1, Integer::sum);
            }
        }
        return demand;
    }

    private static void addSlots(List<LocalDateTime> target, LocalDate day, LocalTime start, LocalTime end) {
        for (LocalTime time = start; time.isBefore(end); time = time.plusMinutes(INTERVIEW_DURATION_MINUTES)) {
            target.add(LocalDateTime.of(day, time));
        }
    }
}
