package club.boyuan.official.integration.feishu;

import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableResponseDTO;
import club.boyuan.official.integration.feishu.dto.ImportFeishuRequestDTO;
import club.boyuan.official.integration.feishu.dto.ImportFeishuResponseDTO;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 飞书导入异步任务快照（JSON 存 Redis）。
 * <p>提交时写入请求参数；执行过程中更新 status 与计数；完成后带上 {@link #result} 明细。
 */
@Data
@Accessors(chain = true)
public class FeishuSyncTaskRecord {

    private Long taskId;
    /** {@link FeishuSyncTaskType} 名称；缺省视为 PUSH_TO_FEISHU */
    private String taskType;
    /** 招聘周期，对应 interview_schedule.cycle_id */
    private Integer cycleId;
    /** 非空时只导入该场次；空则导入周期内全部待同步记录 */
    private Integer slotId;
    /** 请求级覆盖：若填写则所有桶都用此 URL，否则用各 slot 上的 feishu_table_url */
    private String feishuTableUrl;
    /** true=包含已 sync 的记录（飞书会追加行，可能重复） */
    private Boolean forceUpdate;
    /** {@link FeishuSyncTaskStatus} 名称 */
    private String status;
    private Integer importedCount;
    private Integer failedCount;
    private Integer skippedCount;
    /** 总步骤数（PUSH=地点桶数，PULL=表格行数） */
    private Integer totalSteps;
    /** 已完成步骤数 */
    private Integer completedSteps;
    /** 0–100，执行中进度 */
    private Integer progressPercent;
    /** 拉回任务：是否同步更新 user.dept_id */
    private Boolean updateUserDept;
    /** 拉回任务：操作人 userId（决定人缺省时的 fallback） */
    private Integer operatorUserId;
    /** 平台 → 飞书：按地点分组的导入明细 */
    private ImportFeishuResponseDTO result;
    /** 飞书 → 平台：按行导入明细 */
    private ImportFromFeishuTableResponseDTO pullResult;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** MQ 消费时把 Redis 里存的参数还原成执行器入参。 */
    public FeishuSyncTaskType resolvedTaskType() {
        if (!StringUtils.hasText(taskType)) {
            return FeishuSyncTaskType.PUSH_TO_FEISHU;
        }
        return FeishuSyncTaskType.valueOf(taskType);
    }

    public ImportFeishuRequestDTO toImportRequest() {
        ImportFeishuRequestDTO request = new ImportFeishuRequestDTO();
        request.setCycleId(cycleId);
        request.setSlotId(slotId);
        request.setFeishuTableUrl(feishuTableUrl);
        request.setForceUpdate(Boolean.TRUE.equals(forceUpdate));
        return request;
    }

    public ImportFromFeishuTableRequestDTO toPullRequest() {
        ImportFromFeishuTableRequestDTO request = new ImportFromFeishuTableRequestDTO();
        request.setCycleId(cycleId);
        request.setFeishuTableUrl(feishuTableUrl);
        request.setUpdateUserDept(updateUserDept);
        return request;
    }
}
