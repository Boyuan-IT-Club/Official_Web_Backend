package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生更新「能否到线下参加面试」。
 *
 * 该选择存在简历字段 expected_interview_time 的 JSON 里（canAttend / customTime），
 * 简历锁定（评审中）或投递期结束后学生改不了简历表单——但改「线上/线下」
 * 恰恰多发生在那之后（分配已跑完、情况有变），所以单独开这个口子，
 * 规则对齐志愿修改：已排上场次才锁，其余时间随时可改。
 */
@Data
public class UpdateAttendanceRequestDTO {

    @NotNull(message = "招募周期不能为空")
    private Integer cycleId;

    /** true=能到线下参加；false=不能（转线上面试） */
    @NotNull(message = "请选择能否到线下参加")
    private Boolean canAttendOffline;

    /** 不能线下时的情况说明，管理端「待约线上面试」名单直接展示 */
    @Size(max = 200, message = "情况说明最多 200 字")
    private String customTime;
}
