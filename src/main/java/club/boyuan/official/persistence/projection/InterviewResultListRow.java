package club.boyuan.official.persistence.projection;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 录取结果分页 SQL 的联表投影。 */
@Data
public class InterviewResultListRow {
    private Integer resultId;
    private Integer scheduleId;
    private Integer userId;
    private Integer resumeId;
    private Integer cycleId;
    private Integer decision;
    private Integer assignedDeptId;
    private Integer decisionBy;
    private LocalDateTime decisionAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime notifiedAt;
    private String mailState;
    private String userName;
    private LocalDateTime interviewTime;
    private Integer resumeScore;
    private BigDecimal evalTotalScore;
    private Integer evalRecommendation;
    private String firstDeptName;
    private String secondDeptName;
    private Boolean onlineInterview;
    private String departmentName;
}
