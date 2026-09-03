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
    /**
     * 主部门。保留是为了兼容既有调用方与飞书同步等只认单值的地方；
     * 真正的覆盖范围以 deptIds 为准，未传 deptIds 时视同只服务这一个部门。
     */
    private Integer deptId;

    /**
     * 本场次覆盖的全部部门（V36）。一场可以同时面多个部门的同学——
     * 现实里常常几个部门坐一屋轮流面，拆成几个场次会把容量切碎、
     * 时间地点还得重复录。
     */
    private java.util.List<Integer> deptIds;

    @NotBlank(message = "面试地点不能为空")
    private String location;

    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量至少为1")
    private Integer capacity;

    /** 单人面试时长（分钟），为空默认 10 */
    @Min(value = 1, message = "面试时长至少1分钟")
    private Integer interviewDurationMinutes;
}
