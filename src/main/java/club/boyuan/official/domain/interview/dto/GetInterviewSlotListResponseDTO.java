package club.boyuan.official.domain.interview.dto;

import club.boyuan.official.persistence.entity.InterviewSlot;
import lombok.Data;

import java.util.List;

@Data
public class GetInterviewSlotListResponseDTO {
    private long total;;
    private List<InterviewSlot> list;
}
