package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.*;
import club.boyuan.official.domain.interview.dto.*;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.domain.interview.service.IInterviewBookingService;
import club.boyuan.official.domain.interview.service.InterviewBookingSeckillService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import club.boyuan.official.infra.seckill.InterviewBookingRequestStatusCache;
import club.boyuan.official.infra.sse.AsyncTaskChannel;
import club.boyuan.official.infra.sse.AsyncTaskSseHub;
import com.fasterxml.jackson.databind.ObjectMapper;


/** 旧预约的历史记录与已接单回执。新报名使用面试志愿、时间窗和场次接口。 */
@Deprecated
@RestController
@RequestMapping("/api/interview/booking")
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingController {

    private final IInterviewBookingService interviewBookingService;
    private final InterviewBookingSeckillService interviewBookingSeckillService;
    private final IUserService userService;
    private final AsyncTaskSseHub asyncTaskSseHub;
    private final ObjectMapper objectMapper;

    @RequestMapping(path={"", "/seckill", "/{bookingId}"}, method={RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<ResponseMessage<Void>> retiredWrite() {
        return ResponseEntity.status(410).body(ResponseMessage.error(410, "旧预约已下线，请在面试意向页选择可接受时间。"));
    }

    @GetMapping("/cycles/{cycleId}/segments")
    public ResponseEntity<ResponseMessage<Void>> retiredSegments() {
        return retiredWrite();
    }

    /**
     * 轮询秒杀预约结果。
     */
    @GetMapping("/requests/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingAsyncResultDTO>> getBookingRequestStatus(
            @PathVariable String requestId) {
        Integer userId = getAuthenticatedUserId();
        InterviewBookingAsyncResultDTO status = interviewBookingSeckillService.getRequestStatus(userId, requestId);
        return ResponseEntity.ok(ResponseMessage.success(status));
    }

    /**
     * SSE 订阅秒杀预约结果（终态后连接自动关闭）。仍保留 GET /requests/{id} 作为兜底。
     */
    @GetMapping(value = "/requests/{requestId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamBookingRequest(
            @PathVariable String requestId) throws Exception {
        Integer userId = getAuthenticatedUserId();
        InterviewBookingAsyncResultDTO current = interviewBookingSeckillService.getRequestStatus(userId, requestId);
        SseEmitter emitter = asyncTaskSseHub.register(AsyncTaskChannel.BOOKING, requestId);
        emitter.send(SseEmitter.event().name("status").data(objectMapper.writeValueAsString(current)));
        if (isTerminalBookingStatus(current.getStatus())) {
            emitter.complete();
        }
        return emitter;
    }

    private static boolean isTerminalBookingStatus(String status) {
        return InterviewBookingRequestStatusCache.SUCCESS.equals(status)
                || InterviewBookingRequestStatusCache.FAILED.equals(status);
    }

    /**
     * 查询当前用户在指定周期的预约；未预约时 {@code data} 为 null。
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingDTO>> getMyBooking(
            @RequestParam Integer cycleId) {
        Integer userId = getAuthenticatedUserId();
        log.debug("查询本人面试预约，userId={}, cycleId={}", userId, cycleId);
        InterviewBookingDTO booking = interviewBookingService.getMyBooking(userId, cycleId);
        if (booking == null) {
            log.debug("用户尚未预约，userId={}, cycleId={}", userId, cycleId);
        } else {
            log.debug("查询到预约，userId={}, scheduleId={}, slotId={}",
                    userId, booking.getScheduleId(), booking.getSlotId());
        }
        return ResponseEntity.ok(new ResponseMessage<>(200, "操作成功", booking));
    }

    /**
     * 管理员分页查看某周期全部有效预约。
     */
    @GetMapping("/admin/cycles/{cycleId}")
    @PreAuthorize("hasAnyAuthority('interview:schedule', 'resume:audit')")
    public ResponseEntity<ResponseMessage<InterviewBookingAdminListResponseDTO>> listBookingsForAdmin(
            @PathVariable Integer cycleId,
            @RequestParam(required = false) Boolean hasFineInterviewTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("管理员查询面试预约列表，cycleId={}, hasFineInterviewTime={}, page={}, size={}",
                cycleId, hasFineInterviewTime, page, size);
        InterviewBookingAdminListResponseDTO result = interviewBookingService.listBookingsForAdmin(
                cycleId, hasFineInterviewTime, page, size);
        log.info("管理员预约列表查询完成，cycleId={}, total={}", cycleId, result.getTotal());
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /** 当前登录用户 ID：用户名由 JwtAuthenticationFilter 写入 SecurityContext，此处直接读取 */
    private Integer getAuthenticatedUserId() {
        String username = SecurityUtil.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        return user.getUserId();
    }
}
