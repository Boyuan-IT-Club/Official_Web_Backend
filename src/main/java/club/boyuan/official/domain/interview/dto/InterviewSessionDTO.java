package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 面试场次（部门×时间窗×地点×容量）视图
 */
@Data
public class InterviewSessionDTO {

    private Integer sessionId;
    private Integer cycleId;

    private Integer timeSlotId;
    private String slotName;
    private LocalDate interviewDate;
    private LocalTime startTime;
    private LocalTime endTime;

    /** 主部门。多部门场次下只是其中之一，判定请用 deptIds */
    private Integer deptId;
    private String deptName;

    /** 本场次覆盖的全部部门（V36）。单部门场次里就一个元素 */
    private java.util.List<Integer> deptIds;
    private java.util.List<String> deptNames;

    private String location;
    private Integer capacity;
    private Integer currentOccupied;
    /** 剩余名额 = capacity - currentOccupied */
    private Integer remaining;
    private Integer interviewDurationMinutes;
    private Integer status;
}
