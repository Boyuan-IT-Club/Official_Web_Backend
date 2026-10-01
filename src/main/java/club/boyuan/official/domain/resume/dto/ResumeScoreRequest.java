package club.boyuan.official.domain.resume.dto;

import lombok.Data;

/**
 * 打分请求：PUT /api/resumes/{resumeId}/score。
 *
 * comment 区分「没传」与「传了空串」：没传（null）表示不改动原评语——打分舞台的
 * 快速打分只带分数，不能因此把之前写的评语抹掉；传空串表示清空评语。
 */
@Data
public class ResumeScoreRequest {
    /** 0~100 */
    private Integer score;
    /** 打分评语，可选，≤ 500 字 */
    private String comment;
}
