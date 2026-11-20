package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单行录入 / 更正面试结果。
 *
 * 只能定为通过或未通过：任何路径都不能把已定的结果改回待定——学生端实时读库，
 * 已经看到「已录取」的人刷新后结果消失，比结果错了还难解释。
 * 结果已定时再改就是「更正」，必须填原因（写入 interview_result_change 留痕）。
 * 决定人一律取当前登录用户，不接受客户端传入。
 */
@Data
public class InterviewResultSaveDTO {

    /** 1 通过（录取） / 2 未通过 */
    @NotNull(message = "请选择结果")
    @Min(value = 1, message = "结果只能是 1(通过) 或 2(未通过)")
    @Max(value = 2, message = "结果只能是 1(通过) 或 2(未通过)")
    private Integer decision;

    /** decision=1 时必填；decision=2 时忽略并清空 */
    private Integer assignedDeptId;

    /** 更正原因：结果已定后再改时必填 */
    @Size(max = 255, message = "原因最多 255 字")
    private String reason;
}
