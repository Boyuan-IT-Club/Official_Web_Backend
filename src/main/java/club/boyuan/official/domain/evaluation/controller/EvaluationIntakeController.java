package club.boyuan.official.domain.evaluation.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.evaluation.IEvaluationIntakeService;
import club.boyuan.official.domain.evaluation.dto.EvaluationIntakeRequest;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 intake 端点(ADR-0001):候选人派生仓 Actions 推送加密报告单。
 * 零认证,靠 rate-limit 防刷 + 报告 sha256 幂等;身份来自请求内的 github_username。
 */
@RestController
@RequestMapping("/api/public/evaluations")
@AllArgsConstructor
public class EvaluationIntakeController {

    private final IEvaluationIntakeService intakeService;

    @PostMapping
    public ResponseEntity<ResponseMessage<?>> ingest(@RequestBody EvaluationIntakeRequest request) {
        EvaluationSubmission submission = intakeService.ingest(request);
        return ResponseEntity.ok(ResponseMessage.success(submission.getId()));
    }
}