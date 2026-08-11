package club.boyuan.official.domain.evaluation.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.evaluation.IEvaluationAdminService;
import club.boyuan.official.domain.evaluation.dto.CandidateRow;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端评测(需 evaluation:view):候选人总览/详情/认领 + 内部排行榜。
 * 排行榜 = candidates + sortBy=maxScore,前端复用同一接口。
 */
@RestController
@RequestMapping("/api/admin/evaluations")
@AllArgsConstructor
public class EvaluationAdminController {

    private final IEvaluationAdminService adminService;

    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('evaluation:view')")
    public ResponseEntity<ResponseMessage<PageResultDTO<CandidateRow>>> candidates(
            @RequestParam(required = false) Integer cycleId,
            @RequestParam(required = false) Integer deptId,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(required = false, defaultValue = "all") String claimed,
            @RequestParam(required = false, defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResultDTO<CandidateRow> result = adminService.candidates(
                cycleId, deptId, minScore, maxScore, claimed, sortBy, page, size);
        return ResponseEntity.ok(new ResponseMessage<>(200, "评测候选人列表获取成功", result));
    }

    @GetMapping("/candidates/{key}/submissions")
    @PreAuthorize("hasAuthority('evaluation:view')")
    public ResponseEntity<ResponseMessage<List<EvaluationSubmission>>> submissions(@PathVariable String key) {
        List<EvaluationSubmission> list = adminService.submissions(key);
        return ResponseEntity.ok(new ResponseMessage<>(200, "评测提交历史获取成功", list));
    }

    @GetMapping("/submissions/{id}")
    @PreAuthorize("hasAuthority('evaluation:view')")
    public ResponseEntity<ResponseMessage<EvaluationSubmission>> detail(@PathVariable Long id) {
        EvaluationSubmission submission = adminService.detail(id);
        return ResponseEntity.ok(new ResponseMessage<>(200, "评测提交详情获取成功", submission));
    }

    @PutMapping("/submissions/{id}/claim")
    @PreAuthorize("hasAuthority('evaluation:view')")
    public ResponseEntity<ResponseMessage<EvaluationSubmission>> claim(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Integer userId = body.get("userId") == null ? null : Integer.valueOf(body.get("userId").toString());
        EvaluationSubmission submission = adminService.claim(id, userId);
        return ResponseEntity.ok(new ResponseMessage<>(200, "评测提交认领成功", submission));
    }
}