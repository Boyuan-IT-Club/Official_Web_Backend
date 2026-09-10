package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.NotificationCenterDTO;
import club.boyuan.official.domain.interview.service.NotificationCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 通知中心：管理端一处看全一届招新对外发过哪些邮件、还差谁没发。
 */
@RestController
@RequestMapping("/api/interview/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('interview:result', 'resume:audit')")
public class NotificationCenterController {

    private final NotificationCenterService notificationCenterService;

    @GetMapping("/overview")
    public ResponseEntity<ResponseMessage<NotificationCenterDTO>> overview(@RequestParam Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(notificationCenterService.overview(cycleId)));
    }
}
