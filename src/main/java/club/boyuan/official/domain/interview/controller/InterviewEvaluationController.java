package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.EvaluationBoardDTO;
import club.boyuan.official.domain.interview.dto.EvaluationDimensionDTO;
import club.boyuan.official.domain.interview.dto.EvaluationSummaryDTO;
import club.boyuan.official.domain.interview.dto.SaveEvaluationDimensionsRequestDTO;
import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.domain.interview.service.IEvaluationDimensionService;
import club.boyuan.official.domain.interview.service.IInterviewEvaluationService;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 协同面试评价表的管理接口。
 * <p>
 * 单元格的实时编辑不走这里，而是前端直连协同服务的 WebSocket（/collab）；
 * 本控制器只负责开表、锁定、维度配置与汇总这些「表级」操作。
 *
 * @author dhy
 */
@RestController
@RequestMapping("/api/interview/evaluation")
@RequiredArgsConstructor
@Slf4j
public class InterviewEvaluationController {

    /** 管理员权限码：可读全部、可配置维度、可锁定 */
    /**
     * 「评价表管理级」的判定依据:决定能否跨场次查看候选人简历。
     *
     * 权限拆分后这件事归 interview:board:manage,但拆分是渐进的 ——
     * 阶段一签发的旧令牌里只有 resume:audit,所以两者任一即可,
     * 等旧令牌全部过期后可以只留 interview:board:manage。
     *
     * 这不是注解而是代码里的常量,按 @PreAuthorize 搜索会漏掉它:
     * 只改注解不改这里,会让管理员在接口层放行、却在方法内部被当成普通面试官。
     */
    private static final Set<String> BOARD_ADMIN_AUTHORITIES =
            Set.of("interview:board:manage", "resume:audit");

    private final IEvaluationBoardService evaluationBoardService;
    private final IEvaluationDimensionService evaluationDimensionService;
    private final IInterviewEvaluationService interviewEvaluationService;
    private final IUserService userService;

    /**
     * 开启该周期的协同评价表。已开启时原样返回，重复点击不报错。
     */
    @PostMapping("/cycles/{cycleId}/board")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<EvaluationBoardDTO>> openBoard(@PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(evaluationBoardService.openBoard(cycleId)));
    }

    /**
     * 查询评价表状态。面试官需要据此拿到 docName 才能连上协同服务，故一并放行。
     */
    @GetMapping("/cycles/{cycleId}/board")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'interview:evaluate', 'resume:audit')")
    public ResponseEntity<ResponseMessage<EvaluationBoardDTO>> getBoard(@PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(evaluationBoardService.getBoard(cycleId)));
    }

    /**
     * 锁定/解锁评价表：锁定后协同服务拒绝一切写入，全员只读。
     */
    @PutMapping("/cycles/{cycleId}/board/lock")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<EvaluationBoardDTO>> setLocked(@PathVariable Integer cycleId,
                                                                        @RequestParam boolean locked) {
        return ResponseEntity.ok(ResponseMessage.success(evaluationBoardService.setLocked(cycleId, locked)));
    }

    /**
     * 查询该周期的评分维度模板。
     */
    @GetMapping("/cycles/{cycleId}/dimensions")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'interview:evaluate', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<EvaluationDimensionDTO>>> listDimensions(
            @PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(evaluationDimensionService.listByCycle(cycleId)));
    }

    /**
     * 覆盖式保存评分维度模板：请求中未出现的既有维度会被删除。
     */
    @PutMapping("/cycles/{cycleId}/dimensions")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<EvaluationDimensionDTO>>> saveDimensions(
            @PathVariable Integer cycleId,
            @Valid @RequestBody SaveEvaluationDimensionsRequestDTO request) {
        return ResponseEntity.ok(ResponseMessage.success(
                evaluationDimensionService.replaceDimensions(cycleId, request)));
    }

    /**
     * 全周期评价汇总：候选人 × 面试官矩阵，供录取决定参考。
     */
    @GetMapping("/cycles/{cycleId}/summary")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<EvaluationSummaryDTO>> summary(@PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(interviewEvaluationService.summary(cycleId)));
    }

    /**
     * 评价表内速览候选人简历。
     * <p>
     * 面试官没有 {@code resume:view}，走不了简历库那套接口，这里按场次绑定关系放行，
     * 他能看到的恰好是自己要面的那几个人。
     */
    @GetMapping("/cycles/{cycleId}/candidates/{scheduleId}/resume")
    @PreAuthorize("hasAnyAuthority('interview:board:manage', 'interview:evaluate', 'resume:audit')")
    public ResponseEntity<ResponseMessage<ResumeDTO>> candidateResume(@PathVariable Integer cycleId,
                                                                     @PathVariable Integer scheduleId) {
        User viewer = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        return ResponseEntity.ok(ResponseMessage.success(evaluationBoardService.getCandidateResume(
                cycleId, scheduleId, viewer.getUserId(), hasBoardAdminAuthority())));
    }

    private static boolean hasBoardAdminAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> BOARD_ADMIN_AUTHORITIES.contains(granted.getAuthority()));
    }
}
