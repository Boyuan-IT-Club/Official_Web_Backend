package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 周期 × 地点 → 飞书多维表格链接。
 *
 * 推送面试安排时按地点分桶，每个桶查这里拿到自己的表格链接；
 * 同一地点的多个场次（不同部门/时间窗）共享一条配置。
 */
@Data
@Accessors(chain = true)
@TableName("cycle_location_feishu_table")
public class CycleLocationFeishuTable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("cycle_id")
    private Integer cycleId;

    @TableField("location")
    private String location;

    @TableField("feishu_table_url")
    private String feishuTableUrl;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
