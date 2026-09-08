package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreAdmissionFinalizeRequestDTO {
    @NotNull(message = "招募周期不能为空")
    private Integer cycleId;
}
