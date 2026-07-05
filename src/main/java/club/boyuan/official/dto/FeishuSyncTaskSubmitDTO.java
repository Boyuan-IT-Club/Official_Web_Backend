package club.boyuan.official.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FeishuSyncTaskSubmitDTO {

    private Long taskId;
    private String status;
}
