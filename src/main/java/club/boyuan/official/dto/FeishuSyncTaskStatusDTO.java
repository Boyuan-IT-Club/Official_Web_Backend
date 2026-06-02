package club.boyuan.official.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class FeishuSyncTaskStatusDTO {

    private Long taskId;
    /** {@link club.boyuan.official.feishu.FeishuSyncTaskType} 名称 */
    private String taskType;
    private String status;
    /** PUSH：导入飞书条数；PULL：成功写入平台条数 */
    private Integer importedCount;
    private Integer failedCount;
    private Integer skippedCount;
    private Integer totalSteps;
    private Integer completedSteps;
    private Integer progressPercent;
    private String errorMessage;
    private ImportFeishuResponseDTO result;
    private ImportFromFeishuTableResponseDTO pullResult;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
