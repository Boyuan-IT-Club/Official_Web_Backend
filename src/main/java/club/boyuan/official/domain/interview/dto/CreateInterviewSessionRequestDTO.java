package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建面试场次请求
 */
@Data
public class CreateInterviewSessionRequestDTO {

    @NotNull(message = "招募周期ID不能为空")
    private Integer cycleId;

    @NotNull(message = "时间窗ID不能为空")
    private Integer timeSlotId;

    @NotNull(message = "部门ID不能为空")
    private Integer deptId;

    @NotBlank(message = "面试地点不能为空")
    private String location;

    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量至少为1")
    private Integer capacity;

    /** 单人面试时长（分钟），为空默认 10 */
    @Min(value = 1, message = "面试时长至少1分钟")
    private Integer interviewDurationMinutes;
}
