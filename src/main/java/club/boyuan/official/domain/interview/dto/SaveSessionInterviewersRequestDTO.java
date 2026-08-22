package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 绑定某场次的面试官（整场覆盖语义：传空列表即解绑全部）
 */
@Data
public class SaveSessionInterviewersRequestDTO {

    @NotNull(message = "面试官列表不能为空，解绑请传空数组")
    private List<Integer> userIds;
}
