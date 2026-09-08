package club.boyuan.official.domain.resume.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 简历打分明细的展示视图：谁、几分、什么时候 */
@Data
@Accessors(chain = true)
public class ResumeScoreEntryDTO {
    private Integer scorerId;
    /** 打分人姓名；账号已注销时为 null，前端显示「已注销」 */
    private String scorerName;
    private Integer score;
    private LocalDateTime scoredAt;
}
