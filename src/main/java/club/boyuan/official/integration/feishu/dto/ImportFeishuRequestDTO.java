package club.boyuan.official.integration.feishu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImportFeishuRequestDTO {

    @NotNull(message = "招募活动ID不能为空")
    private Integer cycleId;

    /** 可选：仅导入指定时段下的安排 */
    private Integer slotId;

    /** 可选：覆盖 interview_slot 上配置的飞书表格 URL（仅单 slot 导入时常用） */
    private String feishuTableUrl;

    /** 为 true 时重新导入已同步记录（会追加行，不自动删旧数据） */
    private Boolean forceUpdate = false;
}
