package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招新相关二维码。一张表放三类，靠 qrType 区分：
 *   DEPT       部门群 —— 每部门一张，录取通知按录取部门取对应那张
 *   MAIN_GROUP 社团大群 —— 所有录取者都附
 *   QA_GROUP   招新答疑群 —— 简历填写页展示
 *
 * 按周期存而不是全局：群每一届都会换，挂全局就得每年手动改，
 * 且历史周期发出去的邮件内容无从追溯。
 */
@Data
@Accessors(chain = true)
@TableName("recruitment_qr_code")
public class RecruitmentQrCode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 部门群 */
    public static final String TYPE_DEPT = "DEPT";
    /** 社团大群 */
    public static final String TYPE_MAIN_GROUP = "MAIN_GROUP";
    /** 招新答疑群 */
    public static final String TYPE_QA_GROUP = "QA_GROUP";

    /** 非部门类型的 dept_id 占位值。用 0 而不是 NULL —— 唯一索引允许多个 NULL，
     *  用 NULL 的话大群二维码能被重复插入多条 */
    public static final int NO_DEPT = 0;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("cycle_id")
    private Integer cycleId;

    @TableField("qr_type")
    private String qrType;

    @TableField("dept_id")
    private Integer deptId;

    /** COS 对象键或完整 URL；对外返回前经 CosStorageService 解析成可访问地址 */
    @TableField("image_url")
    private String imageUrl;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
