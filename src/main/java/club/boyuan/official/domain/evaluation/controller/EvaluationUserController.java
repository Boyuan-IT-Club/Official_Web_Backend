package club.boyuan.official.domain.evaluation.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.evaluation.IEvaluationUserService;
import club.boyuan.official.domain.evaluation.dto.TrendPoint;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.User;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端评测中心(本人视角):提交历史 / 最新 / 趋势。
 */
@RestController
@RequestMapping("/api/evaluations")
@AllArgsConstructor
public class EvaluationUserController {

    private final IEvaluationUserService evaluationService;
    private final IUserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<PageResultDTO<EvaluationSubmission>>> me(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResultDTO<EvaluationSubmission> result = evaluationService.page(currentUserId(), page, size);
        return ResponseEntity.ok(new ResponseMessage<>(200, "评测历史获取成功", result));
    }

    @GetMapping("/me/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<EvaluationSubmission>> latest() {
        EvaluationSubmission s = evaluationService.latest(currentUserId());
        return ResponseEntity.ok(new ResponseMessage<>(200, "最新评测获取成功", s));
    }

    @GetMapping("/me/trend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<TrendPoint>>> trend() {
        List<TrendPoint> points = evaluationService.trend(currentUserId());
        return ResponseEntity.ok(new ResponseMessage<>(200, "评测趋势获取成功", points));
    }

    private Integer currentUserId() {
        String username = SecurityUtil.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_LOGIN);
        }
        return user.getUserId();
    }
}