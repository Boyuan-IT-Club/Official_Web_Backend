package club.boyuan.official.domain.feedback.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.domain.feedback.service.IFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class FeedbackAdminController {

    private final IFeedbackService feedbackService;

    @GetMapping
    @PreAuthorize("hasAuthority('feedback:view')")
    public ResponseEntity<ResponseMessage<PageResultDTO<FeedbackAdminView>>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(ResponseMessage.success(feedbackService.pageAll(safePage, safeSize)));
    }
}
