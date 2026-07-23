package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生面试志愿视图
 */
@Data
public class InterviewPreferenceDTO {

    private Integer preferenceId;
    private Integer resumeId;
    private Integer cycleId;

    private Integer firstDeptId;
    private String firstDeptName;
    private Integer secondDeptId;
    private String secondDeptName;

    /** 已勾选的可接受时间窗 */
    private List<InterviewTimeSlotDTO> acceptedTimeSlots;

    private LocalDateTime submittedAt;
}
