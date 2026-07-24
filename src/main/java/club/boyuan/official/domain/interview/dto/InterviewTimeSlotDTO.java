package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 面试时间窗（学生可勾选的大时段）视图
 */
@Data
public class InterviewTimeSlotDTO {

    private Integer timeSlotId;
    private Integer cycleId;
    private String slotName;
    private LocalDate interviewDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer status;
}
