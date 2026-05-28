package club.boyuan.official.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class FeishuSyncTaskStatusDTO {

    private Long taskId;
    private String status;
    private Integer importedCount;
    private Integer failedCount;
    private Integer skippedCount;
    private String errorMessage;
    private ImportFeishuResponseDTO result;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
