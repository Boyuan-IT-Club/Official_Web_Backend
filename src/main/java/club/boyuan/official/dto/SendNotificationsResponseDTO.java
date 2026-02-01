package club.boyuan.official.dto;

import lombok.Data;

import java.util.ArrayList;

@Data
public class SendNotificationsResponseDTO {
    private Integer sentCount;    // 发送数量
    private Integer failedCount;  // 失败数量
    private ArrayList<Integer> failedId;//失败resultId
}
