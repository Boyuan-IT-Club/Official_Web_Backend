package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 场次分配结果（方案B）
 */
@Data
public class SessionAssignmentResultDTO {

    private Integer cycleId;
    private LocalDateTime assignedAt;

    private int assignedCount;
    private int unassignedCount;

    /** 本次成功分配的名单 */
    private List<AssignedItem> assigned = new ArrayList<>();

    /** 无法分配、进入待人工调剂的名单 */
    private List<UnassignedItem> unassigned = new ArrayList<>();

    /**
     * 已分配明细
     */
    @Data
    public static class AssignedItem {
        private Integer scheduleId;
        private Integer resumeId;
        private Integer userId;
        private String name;
        /** 命中的志愿：1 第一志愿 / 2 第二志愿 */
        private Integer matchedChoice;
        private Integer sessionId;
        private Integer deptId;
        private String deptName;
        private String location;
        private LocalDateTime interviewStartTime;
        private LocalDateTime interviewEndTime;
    }

    /**
     * 待调剂明细
     */
    @Data
    public static class UnassignedItem {
        private Integer resumeId;
        private Integer userId;
        private String name;
        private Integer firstDeptId;
        private String firstDeptName;
        private Integer secondDeptId;
        private String secondDeptName;
        private String reason;
    }
}
