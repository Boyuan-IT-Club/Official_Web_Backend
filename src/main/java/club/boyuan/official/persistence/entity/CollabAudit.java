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
 * 协同文档写入审计。
 * CRDT 协议层无法阻止已连接的客户端写任意单元格（其本质是全副本可写），
 * 权限因此靠三层兜底：UI 约束 + 物化时的 origin 校验 + 本表的审计留痕。
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("collab_audit")
public class CollabAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文档名
     */
    @TableField("doc_name")
    private String docName;

    /**
     * 发起变更的用户ID（取自 update 的 origin）
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 本次变更影响的 行/列 键摘要
     */
    @TableField("touched_keys")
    private String touchedKeys;

    /**
     * 0正常 1物化时因越权被丢弃
     */
    @TableField("rejected")
    private Integer rejected;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
