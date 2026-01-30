package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 面试时段配置表
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_slot")
public class InterviewSlot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分配ID
     */
    @TableId(value = "slot_id", type = IdType.AUTO)
    private Integer slotId;

    /**
     * 招募活动ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 面试日期
     */
    @TableField("interview_date")
    private LocalDate interviewDate;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalTime startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private LocalTime endTime;

    /**
     * 面试地点
     */
    @TableField("location")
    private String location;

    /**
     * 面试类型：1(线下面试), 2(线上面试)
     */
    @TableField("interview_type")
    private Integer interviewType;

    /**
     * 会议链接（线上面试用）
     */
    @TableField("meeting_link")
    private String meetingLink;

    /**
     * 最大容量
     */
    @TableField("max_capacity")
    private Integer maxCapacity;

    /**
     * 当前已占用人数
     */
    @TableField("current_occupied")
    private Integer currentOccupied;

    /**
     * 飞书多维表格URL
     */
    @TableField("feishu_table_url")
    private String feishuTableUrl;

    /**
     * 状态：1(可用), 2(已满), 3(关闭)
     */
    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;


}
