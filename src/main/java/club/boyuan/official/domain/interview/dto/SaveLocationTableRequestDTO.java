package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存某地点的飞书表格链接。链接留空表示清除该地点的配置。
 */
@Data
public class SaveLocationTableRequestDTO {

    @NotBlank(message = "地点不能为空")
    private String location;

    private String feishuTableUrl;

    private String remark;
}
