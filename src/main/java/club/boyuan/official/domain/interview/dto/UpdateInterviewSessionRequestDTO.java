package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 更新面试场次请求（字段为空表示不修改）
 */
@Data
public class UpdateInterviewSessionRequestDTO {

    private String location;

    @Min(value = 1, message = "容量至少为1")
    private Integer capacity;

    @Min(value = 1, message = "面试时长至少1分钟")
    private Integer interviewDurationMinutes;

    private Integer status;
}
