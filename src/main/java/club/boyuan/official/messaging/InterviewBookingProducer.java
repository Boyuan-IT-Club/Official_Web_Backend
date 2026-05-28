package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewBookingProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishPersist(InterviewBookingMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.INTERVIEW_BOOKING_QUEUE, message);
        log.info("预约落库消息已投递 requestId={}, userId={}, slotId={}",
                message.getRequestId(), message.getUserId(), message.getSlotId());
    }
}
