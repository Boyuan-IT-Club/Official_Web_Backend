package club.boyuan.official.domain.interview.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 正式结果管理列表的公开字段。草稿和迁移凭据仅由预录取接口读取。 */
@Data
public class InterviewResultListItem {
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
