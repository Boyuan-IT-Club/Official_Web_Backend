package club.boyuan.official.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 创建面试时段请求DTO
 */
@Data
public class CreateInterviewSlotRequestDTO {

    @NotNull(message = "招募活动ID不能为空")
    private Integer cycleId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "面试日期不能为空")
    private LocalDate interviewDate;

    @JsonFormat(pattern = "HH:mm:ss")
    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    private String location;

    /**
     * 该面试场归属部门；为空表示共享/调剂面试场。
     */
    private Integer deptId;

    @NotNull(message = "面试类型不能为空")
    private Integer interviewType; // 1-线下面试，2-线上面试

    private String meetingLink; // 会议链接，线上面试用

    @NotNull(message = "最大容量不能为空")
    private Integer maxCapacity;

    private String feishuTableUrl; // 飞书表格URL
}
