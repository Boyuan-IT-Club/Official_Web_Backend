package club.boyuan.official.domain.interview.dto;

import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PreAdmissionListResponseDTO {
    private long total;
    private List<PreAdmissionDraft> candidates;
    private List<Map<String, Object>> departmentStats;
}
