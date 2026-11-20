package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 录取结果变更留痕（V54）。每次改变 decision 或录取部门都写一行。
 * 已定的结果只能「更正」且必须填原因，这张表就是「谁、何时、为什么改」的唯一依据。
 */
@Data
@Accessors(chain = true)
@TableName("interview_result_change")
public class InterviewResultChange implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ACTION_DECIDE = "DECIDE";
    public static final String ACTION_CORRECT = "CORRECT";
    public static final String ACTION_BATCH = "BATCH";
    public static final String ACTION_FINALIZE = "FINALIZE";
    public static final String ACTION_FEISHU = "FEISHU";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Integer resultId;
    private Integer cycleId;
    private String action;
    private Integer fromDecision;
    private Integer fromDeptId;
    private Integer toDecision;
    private Integer toDeptId;
    private Integer wasNotified;
    private String reason;
    private Integer operatorId;
    private LocalDateTime createdAt;
}
