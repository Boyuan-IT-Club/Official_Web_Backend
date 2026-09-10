package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.RecruitFlowProgressDTO;
import club.boyuan.official.domain.interview.service.RecruitFlowProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 招新流程指引：管理端据此提示「现在该做哪一步」。
 */
@RestController
@RequestMapping("/api/interview/flow")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('interview:result', 'resume:audit')")
public class RecruitFlowController {

    private final RecruitFlowProgressService recruitFlowProgressService;

    @GetMapping("/progress")
    public ResponseEntity<ResponseMessage<RecruitFlowProgressDTO>> progress(@RequestParam Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(recruitFlowProgressService.of(cycleId)));
    }
}
