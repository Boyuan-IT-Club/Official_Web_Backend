package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量录取 / 批量标记未通过。
 *
 * cycleId 是必填的：结果的周期挂在 interview_schedule 上，请求里只给 resultIds 无法判断归属，
 * 带上周期才能把夹带的别届 ID 挡在外面（服务端会据此过滤，不属于该周期的一律跳过）。
 */
@Data
public class BatchDecisionRequestDTO {

    @NotNull(message = "招募周期不能为空")
    private Integer cycleId;

    @NotEmpty(message = "请至少选择一位候选人")
    private List<Integer> resultIds;

    /** 1 通过（录取） / 2 未通过 */
    @NotNull(message = "面试决定不能为空")
    @Min(value = 1, message = "面试决定只能是 1(通过) 或 2(未通过)")
    @Max(value = 2, message = "面试决定只能是 1(通过) 或 2(未通过)")
    private Integer decision;

    /** decision=1 时必填：录取进哪个部门 */
    private Integer assignedDeptId;
}
