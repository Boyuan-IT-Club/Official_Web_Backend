package club.boyuan.official.seckill;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Redis 中缓存的预约请求状态。
 */
@Data
@Accessors(chain = true)
public class InterviewBookingRequestStatusCache {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private String status;
    private String message;
    private Integer userId;
    private Integer scheduleId;
    private Integer slotId;
    private Integer cycleId;
}
