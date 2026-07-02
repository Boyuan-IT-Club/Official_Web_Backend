package club.boyuan.official.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class UpdateInterviewSlotDTO {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate interviewDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String location;

    /**
     * 该面试场归属部门；为空表示共享/调剂面试场。
     */
    private Integer deptId;

    private Integer interviewType; // 1-线下面试，2-线上面试

    private String meetingLink; // 会议链接，线上面试用

    private Integer maxCapacity;

    private String feishuTableUrl; // 飞书表格URL
}
