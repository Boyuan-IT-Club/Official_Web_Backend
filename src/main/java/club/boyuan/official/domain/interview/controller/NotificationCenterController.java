package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.NotificationCenterDTO;
import club.boyuan.official.domain.interview.service.NotificationCenterService;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.infra.notification.InterviewNotificationType;
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

    /**
     * 手动补发挂在面试安排上的通知。
     *
     * type 取 BOOKING_SUCCESS（面试安排通知）/ EVE_REMINDER（前一天）/ DAY_REMINDER（当天）。
     * 只投递没发过的，返回本次入队数与跳过的 id——重复轰炸比漏发更难收场。
     */
    @PostMapping("/send")
    public ResponseEntity<ResponseMessage<java.util.Map<String, Object>>> send(
            @RequestBody java.util.Map<String, Object> body) {
        Integer cycleId = body.get("cycleId") == null ? null
                : Integer.valueOf(String.valueOf(body.get("cycleId")));
        InterviewNotificationType type;
        try {
            type = InterviewNotificationType.valueOf(String.valueOf(body.get("type")));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BusinessExceptionEnum.ILLEGAL_ARGUMENT, "未知的通知类型");
        }
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (body.get("scheduleIds") instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    try {
                        ids.add(Integer.valueOf(String.valueOf(o)));
                    } catch (NumberFormatException ignored) {
                        // 单个脏值不该让整批失败
                    }
                }
            }
        }
        return ResponseEntity.ok(ResponseMessage.success(
                notificationCenterService.sendScheduleNotices(cycleId, type, ids)));
    }
}
