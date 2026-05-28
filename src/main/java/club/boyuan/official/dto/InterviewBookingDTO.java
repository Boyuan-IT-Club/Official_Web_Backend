package club.boyuan.official.dto;

import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class InterviewBookingDTO {

    private Integer scheduleId;
    private Integer resumeId;
    private Integer cycleId;
    private Integer slotId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime interviewTime;

    private Integer status;
    private String notes;
    private Integer syncStatus;
    private Integer notifStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate interviewDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String location;
    private Integer interviewType;
    private String meetingLink;
    private Integer maxCapacity;
    private Integer currentOccupied;
    private String feishuTableUrl;
    private Integer slotStatus;

    public static InterviewBookingDTO from(InterviewSchedule schedule, InterviewSlot slot) {
        InterviewBookingDTO dto = new InterviewBookingDTO();
        dto.setScheduleId(schedule.getScheduleId());
        dto.setResumeId(schedule.getResumeId());
        dto.setCycleId(schedule.getCycleId());
        dto.setSlotId(schedule.getSlotId());
        dto.setInterviewTime(schedule.getInterviewTime());
        dto.setStatus(schedule.getStatus());
        dto.setNotes(schedule.getNotes());
        dto.setSyncStatus(schedule.getSyncStatus());
        dto.setNotifStatus(schedule.getNotifStatus());
        dto.setCreatedAt(schedule.getCreatedAt());
        dto.setUpdatedAt(schedule.getUpdatedAt());
        if (slot != null) {
            dto.setInterviewDate(slot.getInterviewDate());
            dto.setStartTime(slot.getStartTime());
            dto.setEndTime(slot.getEndTime());
            dto.setLocation(slot.getLocation());
            dto.setInterviewType(slot.getInterviewType());
            dto.setMeetingLink(slot.getMeetingLink());
            dto.setMaxCapacity(slot.getMaxCapacity());
            dto.setCurrentOccupied(slot.getCurrentOccupied());
            dto.setFeishuTableUrl(slot.getFeishuTableUrl());
            dto.setSlotStatus(slot.getStatus());
        }
        return dto;
    }
}
