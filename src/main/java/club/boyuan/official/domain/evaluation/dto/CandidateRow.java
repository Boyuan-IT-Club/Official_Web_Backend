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
    /** 是否正式社员(user.is_member);未认领(user_id NULL)时为 null,身份未知。 */
    private Boolean member;
    private Integer latestTotalScore;
    private Integer maxTotalScore;
    private Integer submissionCount;
    private LocalDateTime lastEvaluatedAt;

    /** 未认领 = 尚未匹配到官网用户。 */
    public boolean isClaimed() {
        return userId != null;
    }
}