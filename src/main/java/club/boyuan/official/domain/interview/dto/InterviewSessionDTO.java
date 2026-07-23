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

    private Integer deptId;
    private String deptName;

    private String location;
    private Integer capacity;
    private Integer currentOccupied;
    /** 剩余名额 = capacity - currentOccupied */
    private Integer remaining;
    private Integer interviewDurationMinutes;
    private Integer status;
}
