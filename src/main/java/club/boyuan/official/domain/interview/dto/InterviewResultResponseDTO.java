package club.boyuan.official.domain.interview.dto;

import club.boyuan.official.persistence.entity.InterviewResult;
import lombok.Data;

import java.util.List;

@Data
public class InterviewResultResponseDTO {
    private Long total;
    private List<InterviewResult> interviewResults;
}
