package club.boyuan.official.dto;

import lombok.Data;

import java.util.List;

@Data
public class InterviewBookingAdminListResponseDTO {

    private Long total;
    private List<InterviewBookingDTO> list;
}
