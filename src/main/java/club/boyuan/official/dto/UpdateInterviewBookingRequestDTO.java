package club.boyuan.official.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Data
public class UpdateInterviewBookingRequestDTO {

    /**
     * 兼容旧接口：只改到一个指定 slot。
     */
    private Integer slotId;

    /**
     * 新接口：重新提交多个可面试时段，后端按志愿部门重新分配。
     */
    private List<Integer> slotIds;

    private String notes;

    @AssertTrue(message = "面试时段ID不能为空")
    @JsonIgnore
    public boolean isSlotSelectionPresent() {
        return slotId != null || (slotIds != null && !slotIds.isEmpty());
    }

    @JsonIgnore
    public List<Integer> resolveSlotIds() {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (slotId != null) {
            ids.add(slotId);
        }
        if (slotIds != null) {
            for (Integer id : slotIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }
}
