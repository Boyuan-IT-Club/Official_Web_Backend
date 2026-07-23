package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 更新面试时间窗请求（字段为空表示不修改）
 */
@Data
public class UpdateInterviewTimeSlotRequestDTO {

    private String slotName;
    private LocalDate interviewDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer status;
}
