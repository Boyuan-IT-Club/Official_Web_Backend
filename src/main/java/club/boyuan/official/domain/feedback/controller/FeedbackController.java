package club.boyuan.official.domain.feedback.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.feedback.dto.FeedbackCreateRequest;
import club.boyuan.official.domain.feedback.service.IFeedbackService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.Feedback;
import club.boyuan.official.persistence.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final IFeedbackService feedbackService;
    private final IUserService userService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Feedback>> create(@Valid @RequestBody FeedbackCreateRequest request) {
        return ResponseEntity.ok(ResponseMessage.success(feedbackService.create(currentUserId(), request.getContent())));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<PageResultDTO<Feedback>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ResponseMessage.success(
                feedbackService.pageMine(currentUserId(), safePage(page), safeSize(size))));
    }

    private Integer currentUserId() {
        User user = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_LOGIN);
        }
        return user.getUserId();
    }

    private int safePage(int page) { return Math.max(page, 0); }
    private int safeSize(int size) { return Math.min(Math.max(size, 1), 100); }
}
