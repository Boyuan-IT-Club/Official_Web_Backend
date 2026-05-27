package club.boyuan.official.seckill;

/**
 * 秒杀预约相关 Redis Key 规范。
 */
public final class InterviewBookingRedisKeys {

    public static final String REMAIN_PREFIX = "interview:slot:remain:";
    public static final String USER_CYCLE_LOCK_PREFIX = "booking:seckill:user:cycle:";
    public static final String REQUEST_STATUS_PREFIX = "booking:seckill:request:";
    public static final String IDEMPOTENT_PREFIX = "booking:seckill:idempotent:";
    public static final String PROCESSED_PREFIX = "booking:seckill:processed:";

    private InterviewBookingRedisKeys() {
    }

    public static String remain(Integer slotId) {
        return REMAIN_PREFIX + slotId;
    }

    public static String userCycleLock(Integer userId, Integer cycleId) {
        return USER_CYCLE_LOCK_PREFIX + userId + ":" + cycleId;
    }

    public static String requestStatus(String requestId) {
        return REQUEST_STATUS_PREFIX + requestId;
    }

    public static String idempotent(String idempotencyKey) {
        return IDEMPOTENT_PREFIX + idempotencyKey;
    }

    public static String processed(String requestId) {
        return PROCESSED_PREFIX + requestId;
    }
}
