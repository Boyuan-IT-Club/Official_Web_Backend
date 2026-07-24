package club.boyuan.official.integration.feishu.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FeishuLocationImportResultDTO {

    private String location;
    private String tableUrl;
    private int importedCount;
    private int failedCount;
    private String message;
}
