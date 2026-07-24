package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 学生提交面试志愿请求：至多两个志愿部门 + 一个或多个可接受时间窗。
 */
@Data
public class SubmitInterviewPreferenceRequestDTO {

    @NotNull(message = "招募周期ID不能为空")
    private Integer cycleId;

    /** 第一志愿部门（必填） */
    @NotNull(message = "请选择第一志愿部门")
    private Integer firstDeptId;

    /** 第二志愿部门（可选） */
    private Integer secondDeptId;

    /** 可接受的时间窗ID列表 */
    @NotEmpty(message = "请至少勾选一个可接受的时间窗")
    private List<Integer> timeSlotIds;
}
