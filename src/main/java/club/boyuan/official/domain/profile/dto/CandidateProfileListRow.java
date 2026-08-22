package club.boyuan.official.domain.profile.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 候选档案列表行：有面试安排的候选用户 + 最近面试信息。 */
@Data
public class CandidateProfileListRow {
    private Integer userId;
    private String name;
    private String username;
    private String major;
    private String deptName;
    private String cycleName;
    private LocalDateTime latestInterviewTime;
    private String interviewLocation;
}