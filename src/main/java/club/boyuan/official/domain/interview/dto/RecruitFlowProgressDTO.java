package club.boyuan.official.domain.interview.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 一届招新各环节的产出量，供管理端「流程指引」判断每步做没做。
 *
 * 只回计数不回结论：哪一步算「当前该做的」由前端 buildStages 决定，
 * 后端不做拦截——招新现场常有补录、单约一类的例外。
 */
@Data
@Builder
public class RecruitFlowProgressDTO {

    /** 本届配了几个启用中的简历字段；为 0 时学生投不了简历 */
    private long fieldCount;

    /** 已提交（含已初筛）的简历份数 */
    private long submittedResumes;

    /** 已出初筛结论（通过 4 / 未通过 5）的份数 */
    private long screenedResumes;

    /** 生效中的面试安排数（status=1） */
    private long schedules;

    /** 已提交定稿的面试评价数 */
    private long finalizedEvaluations;

    /** 已进预录取草稿名单的人数 */
    private long preAdmitted;

    /** 已录入录取/未录取决定的结果数 */
    private long decided;

    /** 已发出结果通知的人数 */
    private long notified;
}
