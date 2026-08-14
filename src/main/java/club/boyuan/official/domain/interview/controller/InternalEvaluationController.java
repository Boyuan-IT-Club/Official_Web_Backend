package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.EvaluationBoardSeedDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationRequestDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationResultDTO;
import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.domain.interview.service.IInterviewEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供协同服务（Hocuspocus）调用的内部接口，不面向浏览器。
 * <p>
 * 鉴权走 {@link club.boyuan.official.infra.filter.ServiceTokenAuthenticationFilter} 的共享服务令牌，
 * 而非用户 JWT——协同服务是以自己的身份回写数据，用户身份由请求体里的 originUserId 承载并在服务层校验。
 *
 * @author dhy
 */
@RestController
@RequestMapping("/api/internal/evaluation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalEvaluationController {

    private final IEvaluationBoardService evaluationBoardService;
    private final IInterviewEvaluationService interviewEvaluationService;

    /**
     * 服务令牌探活。协同服务的 /collab/diag 用它判断「后端是否认这把令牌」，
     * 从而把"后端漏配 COLLAB_SERVICE_TOKEN"与"用户没有评价权限"区分开——
     * 两者在浏览器里都表现为 Hocuspocus 的 permission-denied，光看界面无法分辨。
     */
    @GetMapping("/ping")
    public ResponseEntity<ResponseMessage<String>> ping() {
        return ResponseEntity.ok(ResponseMessage.success("pong"));
    }

    /**
     * 拉取播种数据：协同服务在文档首次加载或定时对账时调用，据此构造/刷新 Y.Doc 的行列。
     */
    @GetMapping("/board/{cycleId}/seed")
    public ResponseEntity<ResponseMessage<EvaluationBoardSeedDTO>> seed(@PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(evaluationBoardService.getSeed(cycleId)));
    }

    /**
     * 物化回写：把 Y.Doc 解析出的评价批量 upsert 进业务库。
     * 越权条目逐条丢弃并记审计，不影响同批次的合法数据。
     */
    @PostMapping("/materialize")
    public ResponseEntity<ResponseMessage<MaterializeEvaluationResultDTO>> materialize(
            @Valid @RequestBody MaterializeEvaluationRequestDTO request) {
        return ResponseEntity.ok(ResponseMessage.success(interviewEvaluationService.materialize(request)));
    }
}
