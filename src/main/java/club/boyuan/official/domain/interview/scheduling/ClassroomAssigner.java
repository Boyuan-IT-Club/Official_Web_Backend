package club.boyuan.official.domain.interview.scheduling;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 教室分配管理器：为每个具体面试时间点分配有限的教室（默认 3 间）。
 * <p>
 * 从 {@code InterviewAssignmentServiceImpl} 抽出，行为保持一致。有状态（记录各时间点已用教室数），
 * 非线程安全，按单次分配流程内单实例使用。
 */
public class ClassroomAssigner {

    private static final String[] CLASSROOMS = {"教室1", "教室2", "教室3"};

    private final Map<String, Integer> timeSlotClassroomCounter = new HashMap<>();

    /**
     * 为某个面试时间点分配教室。
     *
     * @return 教室编号；无可用教室时返回 {@code null}
     */
    public String assignClassroom(LocalDateTime interviewTime) {
        String timeKey = interviewTime.toString();
        int currentCount = timeSlotClassroomCounter.getOrDefault(timeKey, 0);
        if (currentCount < CLASSROOMS.length) {
            timeSlotClassroomCounter.put(timeKey, currentCount + 1);
            return CLASSROOMS[currentCount];
        }
        return null;
    }

    /**
     * 指定时间点是否仍有可用教室。
     */
    public boolean hasAvailableClassroom(LocalDateTime interviewTime) {
        String timeKey = interviewTime.toString();
        int currentCount = timeSlotClassroomCounter.getOrDefault(timeKey, 0);
        return currentCount < CLASSROOMS.length;
    }
}
