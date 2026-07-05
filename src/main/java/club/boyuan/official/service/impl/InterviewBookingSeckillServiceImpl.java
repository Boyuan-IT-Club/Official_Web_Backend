package club.boyuan.official.service.impl;

import club.boyuan.official.config.InterviewBookingSeckillProperties;
import club.boyuan.official.dto.CreateInterviewBookingRequestDTO;
import club.boyuan.official.dto.InterviewBookingAsyncResultDTO;
import club.boyuan.official.dto.InterviewBookingDTO;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.entity.RecruitmentCycle;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.messaging.BookingOperationType;
import club.boyuan.official.messaging.InterviewBookingMessage;
import club.boyuan.official.messaging.InterviewBookingProducer;
import club.boyuan.official.outbox.MessageOutboxService;
import club.boyuan.official.outbox.OutboxAggregateType;
import club.boyuan.official.outbox.OutboxEventType;
import club.boyuan.official.seckill.*;
import club.boyuan.official.service.*;
import club.boyuan.official.sse.AsyncTaskChannel;
import club.boyuan.official.sse.AsyncTaskSseHub;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀级面试预约：Lua 预扣 → MQ 异步落库 → MQ 异步通知。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingSeckillServiceImpl implements InterviewBookingSeckillService {

    private static final int SCHEDULE_STATUS_CANCELLED = 2;
    private static final int SCHEDULE_STATUS_ACTIVE = 1;

    private final InterviewBookingSeckillProperties seckillProperties;
    private final InterviewBookingLuaInventoryService luaInventoryService;
    private final InterviewSlotInventoryService slotInventoryService;
    private final InterviewBookingProducer bookingProducer;
    private final MessageOutboxService messageOutboxService;
    private final AsyncTaskSseHub asyncTaskSseHub;
    private final IResumeService resumeService;
    private final IRecruitmentCycleService recruitmentCycleService;
    private final IInterviewSlotService interviewSlotService;
    private final IInterviewScheduleService interviewScheduleService;
    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public InterviewBookingAsyncResultDTO submitSeckillBooking(
            Integer userId, CreateInterviewBookingRequestDTO request, String idempotencyKey) {
        if (!seckillProperties.isEnabled()) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SECKILL_DISABLED);
        }

        if (StringUtils.hasText(idempotencyKey)) {
            InterviewBookingAsyncResultDTO cached = getIdempotentResult(idempotencyKey);
            if (cached != null) {
                return cached;
            }
        }

        validateCycleExists(request.getCycleId());
        Resume resume = requireResume(userId, request.getCycleId());
        if (resume.getStatus() == null || resume.getStatus() < 2) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_SUBMITTED_FOR_BOOKING);
        }
        requireBookableSlotMetadata(request.getSlotId(), request.getCycleId());

        InterviewSchedule existing = findScheduleByResumeAndCycle(resume.getResumeId(), request.getCycleId());
        if (existing != null
                && !Objects.equals(existing.getStatus(), SCHEDULE_STATUS_CANCELLED)
                && !Objects.equals(existing.getSlotId(), request.getSlotId())) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_PROCESSING,
                    "已有有效预约，请使用改期接口");
        }
        if (existing != null
                && Objects.equals(existing.getStatus(), SCHEDULE_STATUS_ACTIVE)
                && Objects.equals(existing.getSlotId(), request.getSlotId())) {
            return buildSuccessResult(existing, null);
        }

        slotInventoryService.syncRemainCacheFromDb(request.getSlotId());

        String requestId = UUID.randomUUID().toString().replace("-", "");
        SeckillLuaResult luaResult = luaInventoryService.preDeduct(
                request.getSlotId(), userId, request.getCycleId(), requestId);

        switch (luaResult) {
            case FULL -> throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
            case CACHE_MISS -> {
                slotInventoryService.syncRemainCacheFromDb(request.getSlotId());
                luaResult = luaInventoryService.preDeduct(
                        request.getSlotId(), userId, request.getCycleId(), requestId);
                if (luaResult == SeckillLuaResult.FULL) {
                    throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
                }
                if (luaResult == SeckillLuaResult.USER_LOCKED) {
                    throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_PROCESSING);
                }
                if (luaResult != SeckillLuaResult.SUCCESS) {
                    throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
                }
            }
            case USER_LOCKED -> throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_PROCESSING);
            case SUCCESS -> { /* continue */ }
        }

        BookingOperationType op = (existing != null && Objects.equals(existing.getStatus(), SCHEDULE_STATUS_CANCELLED))
                ? BookingOperationType.REACTIVATE
                : BookingOperationType.CREATE;

        InterviewBookingMessage message = new InterviewBookingMessage(
                requestId,
                userId,
                resume.getResumeId(),
                request.getCycleId(),
                request.getSlotId(),
                request.getNotes(),
                op,
                existing != null ? existing.getScheduleId() : null
        );

        saveRequestStatus(requestId, pendingStatus(request.getCycleId(), request.getSlotId()));
        enqueueBookingPersistMessage(requestId, message);

        InterviewBookingAsyncResultDTO result = new InterviewBookingAsyncResultDTO()
                .setRequestId(requestId)
                .setStatus(InterviewBookingRequestStatusCache.PENDING)
                .setMessage("预约排队中，请使用 requestId 轮询结果");

        if (StringUtils.hasText(idempotencyKey)) {
            saveIdempotentResult(idempotencyKey, result);
        }
        log.info("秒杀预约已受理 requestId={}, userId={}, slotId={}", requestId, userId, request.getSlotId());
        publishBookingSse(requestId, result, false);
        return result;
    }

    private void enqueueBookingPersistMessage(String requestId, InterviewBookingMessage message) {
        if (messageOutboxService.isEnabled()) {
            messageOutboxService.enqueue(
                    OutboxEventType.INTERVIEW_BOOKING_PERSIST,
                    OutboxAggregateType.INTERVIEW_BOOKING,
                    requestId,
                    message);
        } else {
            bookingProducer.publishPersist(message);
        }
    }

    @Override
    public InterviewBookingAsyncResultDTO getRequestStatus(Integer userId, String requestId) {
        InterviewBookingRequestStatusCache cache = loadRequestStatus(requestId);
        if (cache == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_REQUEST_NOT_FOUND);
        }

        InterviewBookingAsyncResultDTO dto = new InterviewBookingAsyncResultDTO()
                .setRequestId(requestId)
                .setStatus(cache.getStatus())
                .setMessage(cache.getMessage());

        if (InterviewBookingRequestStatusCache.SUCCESS.equals(cache.getStatus())
                && cache.getScheduleId() != null) {
            InterviewSchedule schedule = interviewScheduleService.getById(cache.getScheduleId());
            if (schedule != null) {
                Resume resume = resumeService.getResumeById(schedule.getResumeId());
                if (resume != null && !Objects.equals(resume.getUserId(), userId)) {
                    throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_FORBIDDEN);
                }
                InterviewSlot slot = interviewSlotService.getById(schedule.getSlotId());
                dto.setBooking(InterviewBookingDTO.from(schedule, slot));
            }
        }
        return dto;
    }

    @Override
    public void markRequestSuccess(String requestId, InterviewBookingDTO booking) {
        InterviewBookingRequestStatusCache cache = new InterviewBookingRequestStatusCache()
                .setStatus(InterviewBookingRequestStatusCache.SUCCESS)
                .setMessage("预约成功")
                .setScheduleId(booking.getScheduleId())
                .setSlotId(booking.getSlotId())
                .setCycleId(booking.getCycleId());
        saveRequestStatus(requestId, cache);
        stringRedisTemplate.opsForValue().set(
                InterviewBookingRedisKeys.processed(requestId),
                "1",
                seckillProperties.getRequestStatusTtlHours(),
                TimeUnit.HOURS);
        InterviewBookingAsyncResultDTO dto = new InterviewBookingAsyncResultDTO()
                .setRequestId(requestId)
                .setStatus(InterviewBookingRequestStatusCache.SUCCESS)
                .setMessage("预约成功")
                .setBooking(booking);
        publishBookingSse(requestId, dto, true);
    }

    @Override
    public void markRequestFailed(String requestId, String message, Integer slotId, Integer userId, Integer cycleId) {
        luaInventoryService.rollbackPreDeduct(slotId, userId, cycleId);
        InterviewBookingRequestStatusCache cache = new InterviewBookingRequestStatusCache()
                .setStatus(InterviewBookingRequestStatusCache.FAILED)
                .setMessage(message)
                .setSlotId(slotId)
                .setCycleId(cycleId);
        saveRequestStatus(requestId, cache);
        publishBookingSse(requestId, buildAsyncResultFromCache(requestId, cache), true);
    }

    private void publishBookingSse(String requestId, InterviewBookingAsyncResultDTO dto, boolean terminal) {
        asyncTaskSseHub.publish(AsyncTaskChannel.BOOKING, requestId, dto, terminal);
    }

    private InterviewBookingAsyncResultDTO buildAsyncResultFromCache(
            String requestId, InterviewBookingRequestStatusCache cache) {
        return new InterviewBookingAsyncResultDTO()
                .setRequestId(requestId)
                .setStatus(cache.getStatus())
                .setMessage(cache.getMessage());
    }

    private InterviewBookingAsyncResultDTO buildSuccessResult(InterviewSchedule schedule, String requestId) {
        InterviewSlot slot = interviewSlotService.getById(schedule.getSlotId());
        return new InterviewBookingAsyncResultDTO()
                .setRequestId(requestId)
                .setStatus(InterviewBookingRequestStatusCache.SUCCESS)
                .setMessage("预约成功")
                .setBooking(InterviewBookingDTO.from(schedule, slot));
    }

    private InterviewBookingRequestStatusCache pendingStatus(Integer cycleId, Integer slotId) {
        return new InterviewBookingRequestStatusCache()
                .setStatus(InterviewBookingRequestStatusCache.PENDING)
                .setMessage("预约处理中")
                .setCycleId(cycleId)
                .setSlotId(slotId);
    }

    private void saveRequestStatus(String requestId, InterviewBookingRequestStatusCache cache) {
        try {
            stringRedisTemplate.opsForValue().set(
                    InterviewBookingRedisKeys.requestStatus(requestId),
                    objectMapper.writeValueAsString(cache),
                    seckillProperties.getRequestStatusTtlHours(),
                    TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化预约状态失败", e);
        }
    }

    private InterviewBookingRequestStatusCache loadRequestStatus(String requestId) {
        String json = stringRedisTemplate.opsForValue().get(InterviewBookingRedisKeys.requestStatus(requestId));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewBookingRequestStatusCache.class);
        } catch (JsonProcessingException e) {
            log.warn("反序列化预约状态失败 requestId={}", requestId, e);
            return null;
        }
    }

    private void saveIdempotentResult(String idempotencyKey, InterviewBookingAsyncResultDTO result) {
        try {
            stringRedisTemplate.opsForValue().set(
                    InterviewBookingRedisKeys.idempotent(idempotencyKey),
                    objectMapper.writeValueAsString(result),
                    seckillProperties.getRequestStatusTtlHours(),
                    TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("保存幂等结果失败 key={}", idempotencyKey, e);
        }
    }

    private InterviewBookingAsyncResultDTO getIdempotentResult(String idempotencyKey) {
        String json = stringRedisTemplate.opsForValue().get(InterviewBookingRedisKeys.idempotent(idempotencyKey));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewBookingAsyncResultDTO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void validateCycleExists(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }

    private Resume requireResume(Integer userId, Integer cycleId) {
        Resume resume = resumeService.getResumeByUserIdAndCycleId(userId, cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        return resume;
    }

    private void requireBookableSlotMetadata(Integer slotId, Integer cycleId) {
        InterviewSlot slot = interviewSlotService.getById(slotId);
        if (slot == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_NOT_FOUND);
        }
        if (!Objects.equals(slot.getCycleId(), cycleId)) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_CYCLE_MISMATCH);
        }
        if (Objects.equals(slot.getStatus(), 3)) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_CLOSED);
        }
    }

    private InterviewSchedule findScheduleByResumeAndCycle(Integer resumeId, Integer cycleId) {
        LambdaQueryWrapper<InterviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSchedule::getResumeId, resumeId)
                .eq(InterviewSchedule::getCycleId, cycleId)
                .last("LIMIT 1");
        return interviewScheduleService.getOne(wrapper);
    }
}
