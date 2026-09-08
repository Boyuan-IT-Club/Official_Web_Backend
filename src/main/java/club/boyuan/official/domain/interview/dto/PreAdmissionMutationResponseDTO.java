package club.boyuan.official.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PreAdmissionMutationResponseDTO {
    private int affected;
    private List<Integer> skipped;
}
