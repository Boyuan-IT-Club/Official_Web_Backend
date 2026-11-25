package club.boyuan.official.domain.interview.dto;


import lombok.Data;

import java.util.List;

@Data
public class InterviewResultResponseDTO {
    private Long total;
    private List<InterviewResultListItem> interviewResults;
}
