package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.util.ArrayList;

@Data
public class SendNotificationsResponseDTO {
    private Integer queued;
    private Integer sentCount;    // 兼容旧版调用方，表示受理数
    private Integer failedCount;  // 失败数量
    private ArrayList<Integer> failedId;//失败resultId
}
