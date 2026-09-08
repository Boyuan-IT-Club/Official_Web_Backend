package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PreAdmissionRemoveRequestDTO {
    @NotNull(message = "招募周期不能为空")
    private Integer cycleId;
    @NotEmpty(message = "请至少选择一位候选人")
    private List<Integer> resultIds;
}
