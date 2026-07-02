package club.boyuan.official.controller;

import club.boyuan.official.dto.*;
import club.boyuan.official.entity.User;
import club.boyuan.official.config.InterviewBookingSeckillProperties;
import club.boyuan.official.service.IInterviewBookingService;
import club.boyuan.official.service.InterviewBookingSeckillService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import club.boyuan.official.seckill.InterviewBookingRequestStatusCache;
import club.boyuan.official.sse.AsyncTaskChannel;
import club.boyuan.official.sse.AsyncTaskSseHub;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 面试预约（学生自助 + 管理员查询）。
 * <p>
 * 学生先选 {@link InterviewBookableSlotDTO} 中的大时段（slotId），写入 {@code interview_schedule}；
 * 预约成功时按大时段容量均分并写入精确 {@code interviewTime}；到场前提醒由后续定时任务发送。
 * 路径参数 {@code bookingId} 即 {@code schedule_id}。
 */
@RestController
@RequestMapping("/api/interview/booking")
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingController {

    private final IInterviewBookingService interviewBookingService;
    private final InterviewBookingSeckillService interviewBookingSeckillService;
    private final InterviewBookingSeckillProperties seckillProperties;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    private final AsyncTaskSseHub asyncTaskSseHub;
    private final ObjectMapper objectMapper;

    /**
     * 查询某招募周期下可预约的面试时段（含已约/共计与是否约满）。
     */
    @GetMapping("/cycles/{cycleId}/segments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<InterviewBookableSlotDTO>>> listBookableSlots(
            @PathVariable Integer cycleId,
            @RequestParam(defaultValue = "true") boolean resumeSubmittedOnly,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
        log.info("查询可预约面试时段，userId={}, cycleId={}, resumeSubmittedOnly={}",
                userId, cycleId, resumeSubmittedOnly);
        List<InterviewBookableSlotDTO> slots = interviewBookingService.listBookableSlots(
                userId, cycleId, resumeSubmittedOnly);
        log.info("可预约时段查询完成，userId={}, cycleId={}, 返回 {} 条", userId, cycleId, slots.size());
        return ResponseEntity.ok(ResponseMessage.success(slots));
    }

    /**
     * 创建或覆盖预约：同一用户同一周期仅保留一条有效安排（uk_resume_cycle）。
     */
    /**
     * 创建或更新预约。当 {@code booking.seckill.enabled=true} 时，首次预约/复用取消记录走秒杀异步链路。
     * 请求头可传 {@code Idempotency-Key} 防重复提交。
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody CreateInterviewBookingRequestDTO requestDTO,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
        log.info("提交面试预约，userId={}, cycleId={}, slotId={}, seckill={}",
                userId, requestDTO.getCycleId(), requestDTO.getSlotId(), seckillProperties.isEnabled());

        if (seckillProperties.isEnabled()) {
            InterviewBookingAsyncResultDTO asyncResult = interviewBookingSeckillService.submitSeckillBooking(
                    userId, requestDTO, idempotencyKey);
            if (asyncResult.getBooking() != null
                    && "SUCCESS".equals(asyncResult.getStatus())) {
                return ResponseEntity.ok(ResponseMessage.success(asyncResult.getBooking()));
            }
            return ResponseEntity.accepted().body(ResponseMessage.success(asyncResult));
        }

        InterviewBookingDTO booking = interviewBookingService.createOrUpdateBooking(userId, requestDTO);
        log.info("面试预约成功，userId={}, scheduleId={}, slotId={}",
                userId, booking.getScheduleId(), booking.getSlotId());
        return ResponseEntity.ok(ResponseMessage.success(booking));
    }

    /**
     * 秒杀预约专用入口：Lua 预扣 → MQ 落库 → MQ 通知。
     * 若已存在同一时段有效预约，直接返回 SUCCESS 结果。
     */
    @PostMapping("/seckill")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingAsyncResultDTO>> submitSeckillBooking(
            @Valid @RequestBody CreateInterviewBookingRequestDTO requestDTO,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
        log.info("提交秒杀面试预约，userId={}, cycleId={}, slotId={}",
                userId, requestDTO.getCycleId(), requestDTO.getSlotId());
        InterviewBookingAsyncResultDTO result = interviewBookingSeckillService.submitSeckillBooking(
                userId, requestDTO, idempotencyKey);
        if (result.getBooking() != null
                && InterviewBookingRequestStatusCache.SUCCESS.equals(result.getStatus())) {
            return ResponseEntity.ok(ResponseMessage.success(result));
        }
        return ResponseEntity.accepted().body(ResponseMessage.success(result));
    }

    /**
     * 轮询秒杀预约结果。
     */
    @GetMapping("/requests/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingAsyncResultDTO>> getBookingRequestStatus(
            @PathVariable String requestId,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
        InterviewBookingAsyncResultDTO status = interviewBookingSeckillService.getRequestStatus(userId, requestId);
        return ResponseEntity.ok(ResponseMessage.success(status));
    }

    /**
     * SSE 订阅秒杀预约结果（终态后连接自动关闭）。仍保留 GET /requests/{id} 作为兜底。
     */
    @GetMapping(value = "/requests/{requestId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamBookingRequest(
            @PathVariable String requestId,
            HttpServletRequest request) throws Exception {
        Integer userId = getAuthenticatedUserId(request);
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
            @RequestParam Integer cycleId,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
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
     * 改期：更换为另一条未约满的 slotId，并重新分配精确 {@code interviewTime}。
     */
    @PutMapping("/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingDTO>> rescheduleBooking(
            @PathVariable Integer bookingId,
            @Valid @RequestBody UpdateInterviewBookingRequestDTO requestDTO,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
        log.info("改期面试预约，userId={}, scheduleId={}, newSlotId={}",
                userId, bookingId, requestDTO.getSlotId());
        InterviewBookingDTO booking = interviewBookingService.rescheduleBooking(userId, bookingId, requestDTO);
        log.info("改期成功，userId={}, scheduleId={}, slotId={}",
                userId, booking.getScheduleId(), booking.getSlotId());
        return ResponseEntity.ok(ResponseMessage.success(booking));
    }

    /**
     * 取消预约：释放时段占用，安排状态置为已取消。
     */
    @DeleteMapping("/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Void>> cancelBooking(
            @PathVariable Integer bookingId,
            HttpServletRequest request) {
        Integer userId = getAuthenticatedUserId(request);
        log.info("取消面试预约，userId={}, scheduleId={}", userId, bookingId);
        interviewBookingService.cancelBooking(userId, bookingId);
        log.info("取消面试预约成功，userId={}, scheduleId={}", userId, bookingId);
        return ResponseEntity.ok(ResponseMessage.success());
    }

    /**
     * 管理员分页查看某周期全部有效预约。
     */
    @GetMapping("/admin/cycles/{cycleId}")
    @PreAuthorize("hasAuthority('resume:audit')")
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

    /** 从 JWT 解析当前登录用户 ID */
    private Integer getAuthenticatedUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        String username = jwtTokenUtil.extractUsername(token);
        User user = userService.getUserByUsername(username);
        return user.getUserId();
    }
}
