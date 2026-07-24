package club.boyuan.official.integration.feishu.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class ImportFeishuResponseDTO {

    private int importedCount;
    private int failedCount;
    private int skippedCount;
    private List<FeishuLocationImportResultDTO> locations = new ArrayList<>();
}
