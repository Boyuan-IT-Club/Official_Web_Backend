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
 * 协同文档快照（Yjs encodeStateAsUpdate 的二进制状态）。
 * 由协同服务防抖写入，服务重启后据此恢复文档，因此不需要额外的 LevelDB/MongoDB。
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("collab_doc")
public class CollabDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文档名，如 eval-board:3（doc_name 本身即主键，非自增）
     */
    @TableId(value = "doc_name", type = IdType.INPUT)
    private String docName;

    /**
     * 招募周期ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * Yjs 文档二进制快照；为空表示尚未持久化，协同服务会拉播种数据初始化
     */
    @TableField("state")
    private byte[] state;

    /**
     * 0可编辑 1已锁定（周期出结果后冻结，全员只读）
     */
    @TableField("locked")
    private Integer locked;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
