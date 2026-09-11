package club.boyuan.official.domain.feedback.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.feedback.service.IFeedbackService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class FeedbackAdminController {

    private final IFeedbackService feedbackService;
    private final IUserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('feedback:view')")
    public ResponseEntity<ResponseMessage<PageResultDTO<FeedbackAdminView>>> all(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer handled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(ResponseMessage.success(
                feedbackService.pageAll(category, handled, safePage, safeSize)));
    }

    /** 未处理条数，给管理端顶栏角标用——轻量，前端可以随页面刷新拉 */
    @GetMapping("/unhandled-count")
    @PreAuthorize("hasAuthority('feedback:view')")
    public ResponseEntity<ResponseMessage<java.util.Map<String, Long>>> unhandledCount() {
        return ResponseEntity.ok(ResponseMessage.success(
                java.util.Map.of("count", feedbackService.countUnhandled())));
    }

    /**
     * 标记处理状态。只标不回——管理员要的是「这条我看过、处理完了」，
     * 好让列表默认只剩待办；回复提交人是另一回事。
     */
    @PostMapping("/{feedbackId}/handled")
    @PreAuthorize("hasAuthority('feedback:view')")
    public ResponseEntity<ResponseMessage<String>> markHandled(
            @PathVariable Long feedbackId,
            @RequestParam(defaultValue = "true") boolean handled) {
        User me = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        feedbackService.markHandled(feedbackId, handled, me == null ? null : me.getUserId());
        return ResponseEntity.ok(ResponseMessage.success(handled ? "已标记为处理完成" : "已恢复为未处理"));
    }
}
