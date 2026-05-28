package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import club.boyuan.official.dto.InterviewBookingDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.InterviewBookingSeckillService;
import club.boyuan.official.service.InterviewNotificationService;
import club.boyuan.official.service.impl.InterviewBookingAsyncPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import club.boyuan.official.seckill.InterviewBookingRedisKeys;

import java.util.concurrent.TimeUnit;

/**
 * 消费者：异步将 Redis 预扣结果落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewBookingConsumer {

    private final InterviewBookingAsyncPersistenceService persistenceService;
    private final InterviewBookingSeckillService seckillService;
    private final InterviewNotificationService interviewNotificationService;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.INTERVIEW_BOOKING_QUEUE)
    public void handlePersist(InterviewBookingMessage message) {
        if (message == null || message.getRequestId() == null) {
            log.warn("无效预约落库消息，已忽略");
            return;
        }

        String processedKey = InterviewBookingRedisKeys.processed(message.getRequestId());
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(processedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(first)) {
            log.info("预约消息已处理过，跳过 requestId={}", message.getRequestId());
            return;
        }

        try {
            InterviewBookingDTO booking = persistenceService.persist(message);
            if (booking == null) {
                stringRedisTemplate.delete(processedKey);
                seckillService.markRequestFailed(
                        message.getRequestId(),
                        "名额已满或落库失败",
                        message.getSlotId(),
                        message.getUserId(),
                        message.getCycleId());
                return;
            }

            seckillService.markRequestSuccess(message.getRequestId(), booking);
            interviewNotificationService.enqueueBookingSuccess(booking.getScheduleId(), message.getRequestId());
        } catch (Exception e) {
            log.error("预约落库异常 requestId={}", message.getRequestId(), e);
            stringRedisTemplate.delete(processedKey);
            seckillService.markRequestFailed(
                    message.getRequestId(),
                    "系统繁忙，请稍后重试",
                    message.getSlotId(),
                    message.getUserId(),
                    message.getCycleId());
        }
    }
}
