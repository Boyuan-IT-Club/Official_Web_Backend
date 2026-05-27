package club.boyuan.official.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 秒杀模式预约提交结果（异步处理中或已完成）。
 */
@Data
@Accessors(chain = true)
public class InterviewBookingAsyncResultDTO {

    /** 请求追踪 ID，用于轮询 */
    private String requestId;

    /** PENDING | SUCCESS | FAILED */
    private String status;

    private String message;

    /** status=SUCCESS 时可能有值 */
    private InterviewBookingDTO booking;
}
