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
     * 该用户是否为本周期的面试官(在该周期下至少绑定了一个场次)。
     *
     * 协同服务在 onAuthenticate 里调用它做按周期准入。此前只校验「有没有
     * interview:evaluate 权限」,不校验「是不是这个周期的面试官」——
     * A 周期的面试官可以打开 eval-board:B,看到 B 周期的全部候选人名单与分数。
     * 持有 resume:audit 的管理员不需要走这里,协同服务直接放行。
     */
    @GetMapping("/cycles/{cycleId}/interviewers/{userId}")
    public ResponseEntity<ResponseMessage<Boolean>> isInterviewerOfCycle(@PathVariable Integer cycleId,
                                                                        @PathVariable Integer userId) {
        boolean ok = evaluationBoardService.isInterviewerOfCycle(cycleId, userId);
        if (!ok) {
            log.info("用户 {} 不是周期 {} 的面试官，协同服务将拒绝其连接", userId, cycleId);
        }
        return ResponseEntity.ok(ResponseMessage.success(ok));
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
