package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 简历附件。文件在 COS 私有桶，这里只存对象键。 */
@Data
@Accessors(chain = true)
@TableName("resume_attachment")
public class ResumeAttachment {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("resume_id")
    private Integer resumeId;

    /** 上传者。鉴权时用它判断「是不是本人」，免得每次回表查简历 */
    @TableField("user_id")
    private Integer userId;

    @TableField("cycle_id")
    private Integer cycleId;

    /** 原始文件名，下载时用它 */
    @TableField("file_name")
    private String fileName;

    /**
     * COS 对象键。
     * 只在服务端使用——不像头像那样解析成可直接访问的 URL：
     * 附件是申请人的个人资料，取文件必须经过鉴权。
     */
    @TableField("object_key")
    private String objectKey;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
