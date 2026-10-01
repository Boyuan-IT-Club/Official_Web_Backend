package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 简历打分明细：一人一份简历一条（uk_resume_scorer），改分走 UPDATE。
 * resume.resume_score 存全部明细的平均分（四舍五入），由写入方重算维护。
 */
@Data
@Accessors(chain = true)
@TableName("resume_score_entry")
public class ResumeScoreEntry implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("resume_id")
    private Integer resumeId;

    @TableField("scorer_id")
    private Integer scorerId;

    @TableField("score")
    private Integer score;

    /**
     * 打分评语（可选）。updateStrategy=ALWAYS：updateById 总是写这一列，
     * 这样「清空评语」写 null 也能落库；只改分时 service 会带着原评语写回，不会被抹掉。
     */
    @TableField(value = "comment", updateStrategy = FieldStrategy.ALWAYS)
    private String comment;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
