package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 面试场次与面试官绑定：谁负责面哪一场。
 * 现有 interview_session 只有部门+地点+容量，没有「面试官」概念，本表补上，
 * 用于评价表的「我的待评价」过滤与协同表格的可写范围判定。
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("session_interviewer")
public class SessionInterviewer implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 面试场次ID
     */
    @TableField("session_id")
    private Integer sessionId;

    /**
     * 面试官用户ID
     */
    @TableField("user_id")
    private Integer userId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
