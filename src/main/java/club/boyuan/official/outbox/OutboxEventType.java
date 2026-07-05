package club.boyuan.official.outbox;

/**
 * 发件箱事件类型，Relay 根据类型投递到对应 MQ 队列。
 */
public enum OutboxEventType {

    INTERVIEW_BOOKING_PERSIST,
    FEISHU_SYNC
}
