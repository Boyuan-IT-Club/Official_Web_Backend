package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人工调剂：把某条面试安排一键再分配到另一个有空的场次。
 */
@Data
public class ReassignScheduleRequestDTO {

    @NotNull(message = "目标场次ID不能为空")
    private Integer targetSessionId;
}
