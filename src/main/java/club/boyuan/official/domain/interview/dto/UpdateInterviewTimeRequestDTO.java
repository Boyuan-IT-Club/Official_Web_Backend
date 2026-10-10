package club.boyuan.official.domain.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端：手动把某条面试安排的 interview_time 调整到精确的几点几分。
 */
@Data
public class UpdateInterviewTimeRequestDTO {

    /**
     * 可选：把这条安排改挂到另一个场次。
     *
     * 方案B 下面试房间属于场次（interview_session.location），安排本身不存地点——
     * 要改地点就是换场次。为空表示只改时间、不换场。
     */
    private Integer sessionId;

    /**
     * 面试具体时间，ISO-8601 本地时间，如 2026-04-18T15:30 或 2026-04-18T15:30:00。
     *
     * 这里原先钉死成 {@code @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")}（不含秒），
     * 而两个调用方（面试名单、分配与调剂的「调时间/地点」）发的都是
     * {@code dayjs().format('YYYY-MM-DDTHH:mm:00')} —— 带着字面的 :00。
     * 严格模式解析到第 16 位就抛 DateTimeParseException，整个请求 400，
     * 界面上就是一句「系统异常」。这个功能从加上那天起就没通过。
     *
     * 不写 pattern，交给 Jackson 的 ISO-8601 解析：带秒不带秒都收。
     * 同一个域里的 AssignOnlineRequestDTO 本来就是这么做的，那条路一直是好的。
     */
    @NotNull(message = "面试时间不能为空")
    private LocalDateTime interviewTime;
}
