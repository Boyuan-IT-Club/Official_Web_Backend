package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.PreAdmissionBatchRequestDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionListResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionMutationResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionRemoveRequestDTO;

public interface PreAdmissionService {
    PreAdmissionListResponseDTO list(Integer cycleId, String name, String department, Integer page, Integer size);
    PreAdmissionMutationResponseDTO save(PreAdmissionBatchRequestDTO request);
    PreAdmissionMutationResponseDTO remove(PreAdmissionRemoveRequestDTO request);
    int finalizeCycle(Integer cycleId);
}
