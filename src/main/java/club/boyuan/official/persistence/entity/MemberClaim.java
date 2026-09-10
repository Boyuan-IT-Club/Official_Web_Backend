package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 老社员认领申请（V43）。状态：0待审核 1已通过 2已驳回 */
@Data
@Accessors(chain = true)
@TableName("member_claim")
public class MemberClaim implements Serializable {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(value = "claim_id", type = IdType.AUTO)
    private Integer claimId;

    @TableField("user_id")
    private Integer userId;

    @TableField("real_name")
    private String realName;

    @TableField("student_id")
    private String studentId;

    @TableField("join_year")
    private String joinYear;

    @TableField("dept_id")
    private Integer deptId;

    @TableField("evidence")
    private String evidence;

    @TableField("status")
    private Integer status;

    @TableField("review_note")
    private String reviewNote;

    @TableField("reviewed_by")
    private Integer reviewedBy;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // ── 联表展示字段，管理端列表用 ──
    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private String email;
    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String reviewedByName;
}
