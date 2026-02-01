package club.boyuan.official.dto;

import lombok.Data;

import java.util.List;
@Data
public class SendNotificationsRequestDTO {
    private List<Integer> resultIds;  // 结果ID数组

    private String notificationType;  // 通知类型: email, sms, wechat

    private String customMessage;     // 自定义消息
}
