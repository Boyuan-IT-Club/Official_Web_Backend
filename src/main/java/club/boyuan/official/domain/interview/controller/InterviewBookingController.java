package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.*;
import club.boyuan.official.domain.interview.dto.*;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.infra.config.InterviewBookingSeckillProperties;
import club.boyuan.official.domain.interview.service.IInterviewBookingService;
import club.boyuan.official.domain.interview.service.InterviewBookingSeckillService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.SecurityUtil;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 面试预约（学生自助 + 管理员查询）。
 * <p>
 * <b>已弃用（方案B 下线）：</b>面试改为"学生填志愿部门 + 勾选时间窗
 * （{@code /api/interview/preference}）→ 管理员维护场次（{@code /api/interview/admin}）→
 * 算法一键分配（{@code /api/interview/admin/cycles/{cycleId}/assign}）"。
 * 本控制器的学生自助/秒杀入口默认已随 {@code booking.seckill.enabled=false} 停用，
 * 仅暂时保留以便回退，后续可整体移除。
 * <p>
 * 学生先选 {@link InterviewBookableSlotDTO} 中的大时段（slotId），写入 {@code interview_schedule}；
 * 预约成功时按大时段容量均分并写入精确 {@code interviewTime}；到场前提醒由后续定时任务发送。
 * 路径参数 {@code bookingId} 即 {@code schedule_id}。
 */
@Deprecated
@RestController
@RequestMapping("/api/interview/booking")
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingController {

    private final IInterviewBookingService interviewBookingService;
    private final InterviewBookingSeckillService interviewBookingSeckillService;
    private final InterviewBookingSeckillProperties seckillProperties;
    private final IUserService userService;
    private final AsyncTaskSseHub asyncTaskSseHub;
    private final ObjectMapper objectMapper;

    /**
     * 查询某招募周期下可预约的面试时段（含已约/共计与是否约满）。
     */
    @GetMapping("/cycles/{cycleId}/segments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<InterviewBookableSlotDTO>>> listBookableSlots(
            @PathVariable Integer cycleId,
            @RequestParam(defaultValue = "true") boolean resumeSubmittedOnly) {
        Integer userId = getAuthenticatedUserId();
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Integer userId = getAuthenticatedUserId();
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
     * 秒杀预约专用入口（始终异步）：Lua 预扣 → MQ 落库 → MQ 通知。
     */
    @PostMapping("/seckill")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingAsyncResultDTO>> submitSeckillBooking(
            @Valid @RequestBody CreateInterviewBookingRequestDTO requestDTO,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Integer userId = getAuthenticatedUserId();
        log.info("提交秒杀面试预约，userId={}, cycleId={}, slotId={}",
                userId, requestDTO.getCycleId(), requestDTO.getSlotId());
        InterviewBookingAsyncResultDTO result = interviewBookingSeckillService.submitSeckillBooking(
                userId, requestDTO, idempotencyKey);
        return ResponseEntity.accepted().body(ResponseMessage.success(result));
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
     * 改期：更换为另一条未约满的 slotId，并重新分配精确 {@code interviewTime}。
     */
    @PutMapping("/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewBookingDTO>> rescheduleBooking(
            @PathVariable Integer bookingId,
            @Valid @RequestBody UpdateInterviewBookingRequestDTO requestDTO) {
        Integer userId = getAuthenticatedUserId();
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
            @PathVariable Integer bookingId) {
        Integer userId = getAuthenticatedUserId();
        log.info("取消面试预约，userId={}, scheduleId={}", userId, bookingId);
        interviewBookingService.cancelBooking(userId, bookingId);
        log.info("取消面试预约成功，userId={}, scheduleId={}", userId, bookingId);
        return ResponseEntity.ok(ResponseMessage.success());
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
