package club.boyuan.official.integration.feishu.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ImportFromFeishuRowResultDTO {

    private String recordId;
    private String name;
    private String department;
    private boolean success;
    private String message;
    private Integer userId;
    private Integer resultId;
}
