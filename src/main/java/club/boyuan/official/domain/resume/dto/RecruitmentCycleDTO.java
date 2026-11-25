package club.boyuan.official.domain.resume.dto;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 通用周期接口的字段清单。群码仅通过招新群码和录取接口提供。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecruitmentCycleDTO(
        Integer cycleId, String cycleName, String description,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        String academicYear, Integer status, Integer isActive, Integer isDeleted,
        String waitingRoom, String onlineMeetingLink, Integer rescheduleOpen, String contactInfo,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {

    public static RecruitmentCycleDTO from(RecruitmentCycle cycle) {
        if (cycle == null) {
            return null;
        }
        return new RecruitmentCycleDTO(cycle.getCycleId(), cycle.getCycleName(), cycle.getDescription(),
                cycle.getStartDate(), cycle.getEndDate(), cycle.getAcademicYear(), cycle.getStatus(),
                cycle.getIsActive(), cycle.getIsDeleted(), cycle.getWaitingRoom(), cycle.getOnlineMeetingLink(),
                cycle.getRescheduleOpen(), cycle.getContactInfo(), cycle.getCreatedAt(), cycle.getUpdatedAt());
    }

    public static PageResultDTO<RecruitmentCycleDTO> fromPage(PageResultDTO<RecruitmentCycle> page) {
        return new PageResultDTO<>(page.getContent().stream().map(RecruitmentCycleDTO::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getCurrentPage(), page.getPageSize(),
                page.isFirst(), page.isLast());
    }

    public RecruitmentCycle toEntity() {
        RecruitmentCycle cycle = new RecruitmentCycle(cycleName, description, startDate, endDate, academicYear);
        cycle.setCycleId(cycleId);
        cycle.setStatus(status);
        cycle.setIsActive(isActive);
        cycle.setWaitingRoom(waitingRoom);
        cycle.setOnlineMeetingLink(onlineMeetingLink);
        cycle.setRescheduleOpen(rescheduleOpen);
        cycle.setContactInfo(contactInfo);
        return cycle;
    }
}
