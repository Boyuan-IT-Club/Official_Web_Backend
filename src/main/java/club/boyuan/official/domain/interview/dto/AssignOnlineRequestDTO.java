package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 安排线上面试的入参 */
@Data
public class AssignOnlineRequestDTO {

    /**
     * 面试时间。
     * 为空时沿用这条安排上已有的时间——「把已排好的人原地转成线上」是最常见的
     * 用法，时间本来就不用动，不该逼管理员再填一遍。
     */
    private LocalDateTime interviewTime;
}
