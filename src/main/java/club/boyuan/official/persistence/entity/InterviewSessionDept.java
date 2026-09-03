package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

/** 面试场次覆盖的部门。一场可以同时面多个部门的同学（V36）。 */
@Data
@Accessors(chain = true)
@TableName("interview_session_dept")
public class InterviewSessionDept {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("session_id")
    private Integer sessionId;

    @TableField("dept_id")
    private Integer deptId;
}
