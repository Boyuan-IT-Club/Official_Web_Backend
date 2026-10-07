package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手动调整面试时间的返回：更新后的关键状态，以及可能的可读告警。
 * <p>越界（超出该场次时间窗）或同场次时间冲突不拒绝，只写进 {@code warning}。</p>
 */
@Data
public class UpdateInterviewTimeResponseDTO {

    private Integer scheduleId;
    private LocalDateTime interviewTime;

    /** 调整后所在场次；未换场时即原场次 */
    private Integer sessionId;

    /** 调整后的面试地点（取自场次），便于前端就地回显 */
    private String location;

    /** 是否人工指定（本次调整后恒为 1） */
    private Integer timeOverridden;

    /** 已重置为 0，用于触发飞书重新同步 */
    private Integer syncStatus;

    /** 已重置为 0，标记「需重新通知」 */
    private Integer notifStatus;

    /** 可读告警；无告警时为 null */
    private String warning;
}
