package club.boyuan.official.domain.interview.dto;

import lombok.Data;

@Data
public class InterviewResultSaveDTO {
    private Integer decision;
    private Integer assignedDeptId;
    private Integer decisionBy;
}
