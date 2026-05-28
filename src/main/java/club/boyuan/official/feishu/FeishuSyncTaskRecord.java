package club.boyuan.official.feishu;

import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 飞书导入异步任务快照（JSON 存 Redis）。
 * <p>提交时写入请求参数；执行过程中更新 status 与计数；完成后带上 {@link #result} 明细。
 */
@Data
@Accessors(chain = true)
public class FeishuSyncTaskRecord {

    private Long taskId;
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
    /** 按地点分组的导入明细，轮询接口会返回给前端 */
    private ImportFeishuResponseDTO result;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** MQ 消费时把 Redis 里存的参数还原成执行器入参。 */
    public ImportFeishuRequestDTO toImportRequest() {
        ImportFeishuRequestDTO request = new ImportFeishuRequestDTO();
        request.setCycleId(cycleId);
        request.setSlotId(slotId);
        request.setFeishuTableUrl(feishuTableUrl);
        request.setForceUpdate(Boolean.TRUE.equals(forceUpdate));
        return request;
    }
}
