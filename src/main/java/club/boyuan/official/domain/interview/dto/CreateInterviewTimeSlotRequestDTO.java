package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 创建面试时间窗请求
 */
@Data
public class CreateInterviewTimeSlotRequestDTO {

    @NotNull(message = "招募周期ID不能为空")
    private Integer cycleId;

    @NotBlank(message = "时段名称不能为空")
    private String slotName;

    @NotNull(message = "面试日期不能为空")
    private LocalDate interviewDate;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;
}
