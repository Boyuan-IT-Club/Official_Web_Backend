package club.boyuan.official.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 面试预约异步落库消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewBookingMessage implements Serializable {

    private String requestId;
    private Integer userId;
    private Integer resumeId;
    private Integer cycleId;
    private Integer slotId;
    private String notes;
    private BookingOperationType operationType;
    /** REACTIVATE 时必填 */
    private Integer existingScheduleId;
}
