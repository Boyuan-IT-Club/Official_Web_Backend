package club.boyuan.official.dto;

import club.boyuan.official.entity.InterviewSlot;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class InterviewBookableSlotDTO {

    private Integer slotId;
    private Integer cycleId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate interviewDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String location;
    private Integer deptId;
    private Integer interviewType;
    private String meetingLink;
    private Integer maxCapacity;
    private Integer currentOccupied;
    private String feishuTableUrl;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private Boolean fullyBooked;

    public static InterviewBookableSlotDTO from(InterviewSlot slot) {
        InterviewBookableSlotDTO dto = new InterviewBookableSlotDTO();
        dto.setSlotId(slot.getSlotId());
        dto.setCycleId(slot.getCycleId());
        dto.setInterviewDate(slot.getInterviewDate());
        dto.setStartTime(slot.getStartTime());
        dto.setEndTime(slot.getEndTime());
        dto.setLocation(slot.getLocation());
        dto.setDeptId(slot.getDeptId());
        dto.setInterviewType(slot.getInterviewType());
        dto.setMeetingLink(slot.getMeetingLink());
        dto.setMaxCapacity(slot.getMaxCapacity());
        dto.setCurrentOccupied(slot.getCurrentOccupied() == null ? 0 : slot.getCurrentOccupied());
        dto.setFeishuTableUrl(slot.getFeishuTableUrl());
        dto.setStatus(slot.getStatus());
        dto.setCreatedAt(slot.getCreatedAt());
        dto.setUpdatedAt(slot.getUpdatedAt());
        int occupied = dto.getCurrentOccupied();
        int max = slot.getMaxCapacity() == null ? 0 : slot.getMaxCapacity();
        dto.setFullyBooked(slot.getStatus() != null && slot.getStatus() == 2
                || (max > 0 && occupied >= max));
        return dto;
    }
}
