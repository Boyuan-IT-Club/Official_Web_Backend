package club.boyuan.official.dto;

import club.boyuan.official.entity.InterviewSlot;
import lombok.Data;

import java.util.List;

@Data
public class GetInterviewSlotListResponseDTO {
    private long total;;
    private List<InterviewSlot> list;
}
