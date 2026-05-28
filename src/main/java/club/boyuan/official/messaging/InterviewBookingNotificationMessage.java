package club.boyuan.official.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 预约成功后的异步通知消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewBookingNotificationMessage implements Serializable {

    private String requestId;
    private Integer userId;
    private Integer scheduleId;
    private Integer cycleId;
    private Integer slotId;
    private String email;
    private String userName;
}
