package club.boyuan.official.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInterviewBookingRequestDTO {

    @NotNull(message = "面试时段ID不能为空")
    private Integer slotId;

    private String notes;
}
