package club.boyuan.official.dto;

import club.boyuan.official.entity.InterviewResult;
import lombok.Data;

import java.util.List;

@Data
public class InterviewResultResponseDTO {
    private Long total;
    private List<InterviewResult> interviewResults;
}
