package club.boyuan.official.domain.evaluation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端候选人聚合行(排行榜/总览共用)。
 */
@Data
public class CandidateRow {

    private String githubUsername;
    private Integer userId;
    private String userName;
    private String deptName;
    private Integer latestTotalScore;
    private Integer maxTotalScore;
    private Integer submissionCount;
    private LocalDateTime lastEvaluatedAt;

    /** 未认领 = 尚未匹配到官网用户。 */
    public boolean isClaimed() {
        return userId != null;
    }
}