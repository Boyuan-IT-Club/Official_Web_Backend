package club.boyuan.official.messaging;

/**
 * 秒杀异步落库操作类型。
 */
public enum BookingOperationType {
    /** 首次预约 */
    CREATE,
    /** 复用已取消记录 */
    REACTIVATE
}
