package club.boyuan.official.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImportFromFeishuTableRequestDTO {

    @NotNull(message = "招募活动ID不能为空")
    private Integer cycleId;

    @NotBlank(message = "飞书表格 URL 不能为空")
    private String feishuTableUrl;

    /**
     * 为 true 时，同步更新 user.dept_id；默认 true。
     */
    private Boolean updateUserDept = true;
}
