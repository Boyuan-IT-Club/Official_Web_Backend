package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 仅管理端可见的预录取草稿；最终结果仍以 interview_result 为准。 */
@Data
@Accessors(chain = true)
@TableName("pre_admission_draft")
public class PreAdmissionDraft {

    @TableId(value = "draft_id", type = IdType.AUTO)
    private Integer draftId;
    private Integer cycleId;
    private Integer resultId;
    private Integer assignedDeptId;
    private Integer createdBy;
    private Integer updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer userId;
    /** 该同学当前的正式结果（0 待定 / 1 通过 / 2 未通过）：已有结果的草稿是冲突，转正不了 */
    @TableField(exist = false)
    private Integer decision;
    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String departmentName;
}
