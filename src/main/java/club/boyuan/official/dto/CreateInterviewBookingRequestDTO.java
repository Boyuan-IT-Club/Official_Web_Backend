package club.boyuan.official.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Data
public class CreateInterviewBookingRequestDTO {

    @NotNull(message = "招募活动ID不能为空")
    private Integer cycleId;

    /**
     * 兼容旧接口：只提交一个 slotId 时仍可预约。
     */
    private Integer slotId;

    /**
     * 新接口：学生可提交一个或多个可面试大时段，后端按志愿部门从中分配最终时段。
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

    @JsonIgnore
    public boolean isSingleSlotSelection() {
        return resolveSlotIds().size() == 1;
    }
}
