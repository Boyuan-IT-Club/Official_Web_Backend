package club.boyuan.official.domain.evaluation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户端趋势点(按评测时间升序)。 */
@Data
public class TrendPoint {

    private LocalDateTime evaluatedAt;
    private Integer totalScore;
}