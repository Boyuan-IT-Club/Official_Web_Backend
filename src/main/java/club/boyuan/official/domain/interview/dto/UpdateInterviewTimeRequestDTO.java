package club.boyuan.official.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端：手动把某条面试安排的 interview_time 调整到精确的几点几分。
 */
@Data
public class UpdateInterviewTimeRequestDTO {

    /**
     * 面试具体时间，格式 yyyy-MM-dd'T'HH:mm，如 2026-04-18T15:30。
     */
    @NotNull(message = "面试时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime interviewTime;
}
