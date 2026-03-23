package club.boyuan.official.service.impl;

import club.boyuan.official.dto.AutoAssignInterviewResponseDTO;
import club.boyuan.official.dto.SlotTimeDTO;
import club.boyuan.official.entity.*;
import club.boyuan.official.mapper.InterviewScheduleMapper;
import club.boyuan.official.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试安排服务实现类
 */
@Slf4j
@Service
@AllArgsConstructor
public class InterviewScheduleServiceImpl extends ServiceImpl<InterviewScheduleMapper, InterviewSchedule> 
        implements IInterviewScheduleService {

    private final IResumeService resumeService;
    private final IRecruitmentCycleService recruitmentCycleService;
    private final IResumeFieldDefinitionService resumeFieldDefinitionService;
    private final IUserService userService;
    private final IInterviewSlotService interviewSlotService;
    private final ObjectMapper objectMapper;
    private final ResumeDataService resumeDataService;
    
    // 面试时间段定义
    /*private static final LocalTime MORNING_START = LocalTime.of(9, 0);
    private static final LocalTime MORNING_END = LocalTime.of(11, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 30);
    private static final LocalTime EVENING_START = LocalTime.of(19, 0);
    private static final LocalTime EVENING_END = LocalTime.of(21, 0);*/
    private static final int INTERVIEW_DURATION = 10; // 面试时长10分钟

    //获取该cycleId下的面试时间段
    private List<SlotTimeDTO> getInterviewTimeSlots(Integer cycleId) {
        // 获取可用时间槽
        List<InterviewSlot> availableSlots = interviewSlotService.getAvailableSlotsByCycleId(cycleId);

        // 空值检查
        if (availableSlots == null || availableSlots.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换为DTO列表
        return availableSlots.stream()
                .map(slot -> new SlotTimeDTO(
                        slot.getInterviewDate(),
                        slot.getStartTime(),
                        slot.getEndTime()
                ))
                .collect(Collectors.toList());  // 使用collect替代toList()以保证兼容性
    }

    @Override
    @Transactional
    public AutoAssignInterviewResponseDTO autoAssignInterviews(Integer cycleId) {
        log.info("开始为招募周期ID {} 一键分配面试", cycleId);

        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            throw new IllegalArgumentException("招募周期不存在，ID: " + cycleId);
        }

        // 获取该周期下的所有简历，仅处理已提交(status >= 2)
        List<Resume> allResumes = resumeService.getAllResumesByCycleId(cycleId);
        List<Resume> resumes = allResumes.stream()
                .filter(resume -> resume.getStatus() != null && resume.getStatus() >= 2)
                .collect(Collectors.toList());
        log.info("获取到 {} 份简历，其中已提交 {} 份", allResumes.size(), resumes.size());

        // 解析简历偏好：期望面试时间 + 期望部门
        ResumeFieldDefinition interviewTimeField = resumeDataService.getInterviewTimeFieldDefinition(cycleId);
        if (interviewTimeField == null) {
            throw new IllegalStateException("未找到'期望的面试时间'字段定义");
        }

        ResumeFieldDefinition expectedDepartmentsField = resumeDataService.getExpectedDepartmentsFieldDefinition(cycleId);
        if (expectedDepartmentsField == null) {
            throw new IllegalStateException("未找到'期望部门'字段定义");
        }

        Map<Integer, List<String>> userPreferredTimes =
                resumeDataService.getUserPreferredTimes(resumes, interviewTimeField.getFieldId());
        Map<Integer, List<String>> userPreferredDepartments =
                resumeDataService.getUserPreferredDepartments(resumes, expectedDepartmentsField.getFieldId());

        // 取面试时段表中可用的时段，并计算每个 slot 的剩余容量
        List<InterviewSlot> availableSlots = interviewSlotService.getAvailableSlotsByCycleId(cycleId);
        List<LocalDate> sortedInterviewDates = availableSlots.stream()
                .map(InterviewSlot::getInterviewDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<Integer, Integer> remainingCapacityBySlotId = new HashMap<>();
        for (InterviewSlot slot : availableSlots) {
            int maxCapacity = slot.getMaxCapacity() == null ? 0 : slot.getMaxCapacity();
            int currentOccupied = slot.getCurrentOccupied() == null ? 0 : slot.getCurrentOccupied();
            remainingCapacityBySlotId.put(slot.getSlotId(), Math.max(0, maxCapacity - currentOccupied));
        }

        // 使用优化策略分配面试时间（从 interview_slot 表取地点/slot_id）
        return assignInterviewsWithOptimization(
                resumes,
                userPreferredTimes,
                userPreferredDepartments,
                availableSlots,
                sortedInterviewDates,
                remainingCapacityBySlotId,
                cycleId);
    }
    
    /**
     * 使用优化策略分配面试时间
     */
    private AutoAssignInterviewResponseDTO assignInterviewsWithOptimization(
            List<Resume> resumes,
            Map<Integer, List<String>> userPreferredTimes,
            Map<Integer, List<String>> userPreferredDepartments,
            List<InterviewSlot> availableSlots,
            List<LocalDate> sortedInterviewDates,
            Map<Integer, Integer> remainingCapacityBySlotId,
            Integer cycleId) {

        List<CandidateInfo> candidates = new ArrayList<>();
        List<AutoAssignInterviewResponseDTO.UnassignedUserDTO> unassignedUsers = new ArrayList<>();
        List<AutoAssignInterviewResponseDTO.NoPreferenceUserDTO> noPreferenceUsers = new ArrayList<>();

        // slot_id -> slot 映射，方便后续批量更新容量
        Map<Integer, InterviewSlot> slotById = availableSlots.stream()
                .collect(Collectors.toMap(InterviewSlot::getSlotId, s -> s, (a, b) -> a));

        for (Resume resume : resumes) {
            User user = userService.getUserById(resume.getUserId());
            if (user == null) {
                log.warn("简历 {} 对应的用户 {} 不存在", resume.getResumeId(), resume.getUserId());
                continue;
            }

            List<String> preferredTimes = userPreferredTimes.getOrDefault(resume.getUserId(), new ArrayList<>());
            List<String> preferredDepartments = userPreferredDepartments.getOrDefault(resume.getUserId(), new ArrayList<>());

            if (preferredTimes.isEmpty()) {
                noPreferenceUsers.add(new AutoAssignInterviewResponseDTO.NoPreferenceUserDTO(
                        user.getUserId(),
                        user.getUsername(),
                        resumeDataService.getResumeName(resume),
                        resumeDataService.getResumeEmail(resume),
                        resumeDataService.getResumeMajor(resume),
                        resumeDataService.getResumeGrade(resume)
                ));
                continue;
            }

            if (preferredDepartments.isEmpty()) {
                noPreferenceUsers.add(new AutoAssignInterviewResponseDTO.NoPreferenceUserDTO(
                        user.getUserId(),
                        user.getUsername(),
                        resumeDataService.getResumeName(resume),
                        resumeDataService.getResumeEmail(resume),
                        resumeDataService.getResumeMajor(resume),
                        resumeDataService.getResumeGrade(resume)
                ));
                continue;
            }

            String firstDepartment = preferredDepartments.get(0);
            candidates.add(new CandidateInfo(user, preferredTimes, preferredDepartments, firstDepartment, resume));
        }

        // 统计每个期望时间段的需求量（用于“紧迫度排序”）
        Map<String, Integer> timeSlotDemand = new HashMap<>();
        for (CandidateInfo candidate : candidates) {
            for (String timeSlot : candidate.preferredTimes) {
                timeSlotDemand.merge(timeSlot, 1, Integer::sum);
            }
        }

        // 统计每个期望时间段的可用 slot 数（仅基于当前 remainingCapacity）
        Map<String, Integer> timeSlotAvailableCount = new HashMap<>();
        for (String timeSlot : timeSlotDemand.keySet()) {
            int count = 0;
            for (InterviewSlot slot : availableSlots) {
                int remaining = remainingCapacityBySlotId.getOrDefault(slot.getSlotId(), 0);
                if (remaining <= 0) {
                    continue;
                }
                if (isPreferredTimeMatchSlot(slot, timeSlot, sortedInterviewDates)) {
                    count++;
                }
            }
            timeSlotAvailableCount.put(timeSlot, count);
        }

        // 计算每个候选人的紧迫度分数并排序：尽量优先满足“稀缺且竞争激烈”的偏好
        Map<Integer, Double> urgencyScoreByUserId = new HashMap<>();
        for (CandidateInfo candidate : candidates) {
            List<String> preferredTimes = candidate.preferredTimes;
            double baseUrgency = preferredTimes.isEmpty() ? 1.0 : 1.0 / preferredTimes.size();

            double scarcitySum = 0.0;
            for (String timeSlot : preferredTimes) {
                int demand = timeSlotDemand.getOrDefault(timeSlot, 0);
                int availableCount = timeSlotAvailableCount.getOrDefault(timeSlot, 0);
                if (availableCount > 0) {
                    scarcitySum += (double) demand / availableCount;
                } else {
                    scarcitySum += 10.0;
                }
            }
            double scarcityAvg = preferredTimes.isEmpty() ? 0.0 : (scarcitySum / preferredTimes.size());
            double urgencyScore = baseUrgency * 0.6 + scarcityAvg * 0.4;

            urgencyScoreByUserId.put(candidate.user.getUserId(), urgencyScore);
        }

        candidates.sort((c1, c2) -> {
            int cmp = Double.compare(
                    urgencyScoreByUserId.getOrDefault(c2.user.getUserId(), 0.0),
                    urgencyScoreByUserId.getOrDefault(c1.user.getUserId(), 0.0)
            );
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(c1.user.getUserId(), c2.user.getUserId());
        });

        List<AutoAssignInterviewResponseDTO.AssignmentDetailDTO> assignmentDetails = new ArrayList<>();
        List<InterviewSchedule> schedulesToSave = new ArrayList<>();
        Map<Integer, Integer> assignedCountBySlotId = new HashMap<>();

        for (CandidateInfo candidate : candidates) {
            User user = candidate.user;
            Resume resume = candidate.resume;

            boolean assigned = false;
            for (String preferredTime : candidate.preferredTimes) {
                InterviewSlot selectedSlot = null;
                for (InterviewSlot slot : availableSlots) {
                    int remaining = remainingCapacityBySlotId.getOrDefault(slot.getSlotId(), 0);
                    if (remaining <= 0) {
                        continue;
                    }
                    if (isPreferredTimeMatchSlot(slot, preferredTime, sortedInterviewDates)) {
                        selectedSlot = slot;
                        break; // availableSlots 默认已按时间正序
                    }
                }

                if (selectedSlot == null) {
                    continue;
                }

                Integer slotId = selectedSlot.getSlotId();
                int remaining = remainingCapacityBySlotId.getOrDefault(slotId, 0);
                if (remaining <= 0) {
                    continue;
                }

                remainingCapacityBySlotId.put(slotId, remaining - 1);
                assignedCountBySlotId.merge(slotId, 1, Integer::sum);

                LocalDateTime interviewDateTime = LocalDateTime.of(
                        selectedSlot.getInterviewDate(),
                        selectedSlot.getStartTime()
                );
                String period = getTimePeriod(selectedSlot.getStartTime());
                String location = selectedSlot.getLocation();

                AutoAssignInterviewResponseDTO.AssignmentDetailDTO detail = new AutoAssignInterviewResponseDTO.AssignmentDetailDTO();
                detail.setUserId(user.getUserId());
                detail.setUsername(user.getUsername());
                detail.setName(resumeDataService.getResumeName(resume));
                detail.setEmail(resumeDataService.getResumeEmail(resume));
                detail.setSlotId(slotId);
                detail.setInterviewTime(interviewDateTime);
                detail.setLocation(location);
                detail.setPeriod(period);
                detail.setDepartment(candidate.firstDepartment);
                detail.setClassroom(location);
                detail.setPreferredDepartments(getFormattedPreferredDepartments(user.getUserId(), userPreferredDepartments));
                detail.setPreferredTimes(String.join(", ", candidate.preferredTimes));
                assignmentDetails.add(detail);

                InterviewSchedule schedule = new InterviewSchedule()
                        .setResumeId(resume.getResumeId())
                        .setUserId(user.getUserId())
                        .setCycleId(resume.getCycleId())
                        .setSlotId(slotId)
                        .setInterviewTime(interviewDateTime)
                        .setStatus(1)
                        .setNotes("自动分配 - " + period + " - " + location)
                        .setSyncStatus(0)
                        .setNotifStatus(0);
                schedulesToSave.add(schedule);

                assigned = true;
                break;
            }

            if (!assigned) {
                String preferredTimesStr = String.join(", ", candidate.preferredTimes);
                String preferredDepartmentsStr = String.join(", ", candidate.preferredDepartments);
                log.info("用户 {} 未被分配，期望时间: {}，期望部门: {}",
                        user.getUsername(), preferredTimesStr, preferredDepartmentsStr);

                unassignedUsers.add(new AutoAssignInterviewResponseDTO.UnassignedUserDTO(
                        user.getUserId(),
                        user.getUsername(),
                        resumeDataService.getResumeName(resume),
                        resumeDataService.getResumeEmail(resume),
                        resumeDataService.getResumeMajor(resume),
                        resumeDataService.getResumeGrade(resume),
                        preferredTimesStr,
                        preferredDepartmentsStr
                ));
            } else {
                log.info("用户 {} 已成功分配面试时间", user.getUsername());
            }
        }

        // 批量保存面试安排
        if (!schedulesToSave.isEmpty()) {
            this.saveBatch(schedulesToSave);
            log.info("成功保存 {} 条面试安排记录", schedulesToSave.size());
        }

        // 更新面试时段表容量/状态
        if (!assignedCountBySlotId.isEmpty()) {
            List<InterviewSlot> slotsToUpdate = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : assignedCountBySlotId.entrySet()) {
                InterviewSlot slot = slotById.get(entry.getKey());
                if (slot == null) {
                    continue;
                }

                int currentOccupied = slot.getCurrentOccupied() == null ? 0 : slot.getCurrentOccupied();
                int maxCapacity = slot.getMaxCapacity() == null ? 0 : slot.getMaxCapacity();
                int newOccupied = currentOccupied + entry.getValue();

                slot.setCurrentOccupied(newOccupied);
                slot.setStatus(newOccupied >= maxCapacity ? 2 : 1);
                slotsToUpdate.add(slot);
            }

            if (!slotsToUpdate.isEmpty()) {
                interviewSlotService.updateBatchById(slotsToUpdate);
            }
        }

        log.info("面试时间分配完成，已分配 {} 人，未分配 {} 人，未填写期望 {} 人",
                assignmentDetails.size(), unassignedUsers.size(), noPreferenceUsers.size());

        AutoAssignInterviewResponseDTO response = new AutoAssignInterviewResponseDTO();
        response.setAssignedCount(assignmentDetails.size());
        response.setUnassignedCount(unassignedUsers.size());
        response.setNoPreferenceCount(noPreferenceUsers.size());
        response.setAssignmentDetails(assignmentDetails);
        response.setUnassignedUsers(unassignedUsers);
        response.setNoPreferenceUsers(noPreferenceUsers);
        response.setAssignmentTime(LocalDateTime.now());

        return response;
    }

    /**
     * 将“期望的面试时间”匹配到某个可用 slot：
     * 简历期望格式：`interview_date start_time~end_time`
     * 例如：`2026-03-23 09:00~11:00`
     */
    private boolean isPreferredTimeMatchSlot(InterviewSlot slot, String preferredTime, List<LocalDate> sortedInterviewDates) {
        if (slot == null || preferredTime == null || preferredTime.trim().isEmpty()) {
            return false;
        }
        if (slot.getInterviewDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            return false;
        }

        PreferredSlotKey key = parsePreferredSlotKey(preferredTime);
        if (key == null) {
            return false;
        }

        return slot.getInterviewDate().equals(key.interviewDate)
                && slot.getStartTime().equals(key.startTime)
                && slot.getEndTime().equals(key.endTime);
    }

    /**
     * 从简历字符串解析出 slotKey（interview_date + start_time + end_time）。
     * 允许 start/end 带秒或不带秒。
     */
    private PreferredSlotKey parsePreferredSlotKey(String preferredTime) {
        if (preferredTime == null) {
            return null;
        }

        // 兼容：interview_date start~end 以及两端可能有秒
        // date: yyyy-MM-dd
        // time: HH:mm 或 HH:mm:ss
        // join: ~
        String trimmed = preferredTime.trim();
        // 去掉全角波浪号
        trimmed = trimmed.replace("～", "~");

        // 例：2026-03-23 09:00~11:00
        // 例：2026-03-23 09:00:00~11:00:00
        String[] dateAndTimes = trimmed.split("\\s+", 2);
        if (dateAndTimes.length != 2) {
            return null;
        }

        LocalDate interviewDate;
        try {
            interviewDate = LocalDate.parse(dateAndTimes[0], DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.warn("解析简历期望的日期失败: {}", preferredTime, e);
            return null;
        }

        String timesPart = dateAndTimes[1].trim();
        String[] startEnd = timesPart.split("~", 2);
        if (startEnd.length != 2) {
            return null;
        }

        LocalTime startTime = parseFlexibleLocalTime(startEnd[0].trim());
        LocalTime endTime = parseFlexibleLocalTime(startEnd[1].trim());
        if (startTime == null || endTime == null) {
            return null;
        }

        return new PreferredSlotKey(interviewDate, startTime, endTime);
    }

    private LocalTime parseFlexibleLocalTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        // 允许 9:00 / 09:00 / 09:00:00
        // 统一用宽松解析策略：按冒号段数补齐
        String t = timeStr.trim();
        try {
            String[] parts = t.split(":");
            if (parts.length == 2) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                return LocalTime.of(h, m);
            } else if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                return LocalTime.of(h, m, s);
            }
        } catch (Exception e) {
            log.warn("解析简历期望的时间失败: {}", timeStr, e);
            return null;
        }
        return null;
    }

    private static class PreferredSlotKey {
        private final LocalDate interviewDate;
        private final LocalTime startTime;
        private final LocalTime endTime;

        private PreferredSlotKey(LocalDate interviewDate, LocalTime startTime, LocalTime endTime) {
            this.interviewDate = interviewDate;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
    
    /**
     * 从简历中获取姓名字段值
     */
    private String getResumeName(Resume resume) {
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());
        
        ResumeFieldDefinition nameFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "姓名".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
        
        if (nameFieldDefinition != null) {
            String name = fieldValues.stream()
                    .filter(value -> nameFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);
            
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        
        User user = userService.getUserById(resume.getUserId());
        return user != null ? user.getName() : "";
    }
    
    /**
     * 从简历中获取邮箱字段值
     */
    private String getResumeEmail(Resume resume) {
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());
        
        ResumeFieldDefinition emailFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "邮箱".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
        
        if (emailFieldDefinition != null) {
            String email = fieldValues.stream()
                    .filter(value -> emailFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);
            
            if (email != null && !email.isEmpty()) {
                return email;
            }
        }
        
        User user = userService.getUserById(resume.getUserId());
        return user != null ? user.getEmail() : "";
    }
    
    /**
     * 从简历中获取专业字段值
     */
    private String getResumeMajor(Resume resume) {
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());
        
        ResumeFieldDefinition majorFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "专业".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
        
        if (majorFieldDefinition != null) {
            String major = fieldValues.stream()
                    .filter(value -> majorFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);
            
            if (major != null && !major.isEmpty()) {
                return major;
            }
        }
        
        return "";
    }
    
    /**
     * 从简历中获取年级字段值
     */
    private String getResumeGrade(Resume resume) {
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());
        
        ResumeFieldDefinition gradeFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "年级".equals(field.getFieldLabel()) || "大几".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
        
        if (gradeFieldDefinition != null) {
            String grade = fieldValues.stream()
                    .filter(value -> gradeFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);
            
            if (grade != null && !grade.isEmpty()) {
                return grade;
            }
        }
        
        return "";
    }
    
    /**
     * 获取格式化的期望部门字符串
     */
    private String getFormattedPreferredDepartments(Integer userId, Map<Integer, List<String>> userPreferredDepartments) {
        List<String> departments = userPreferredDepartments.get(userId);
        if (departments == null || departments.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < departments.size() && i < 2; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append("第").append(i + 1).append("志愿：").append(departments.get(i));
        }
        
        return sb.toString();
    }
    
    /**
     * 获取"期望的面试时间"字段定义
     */
    private ResumeFieldDefinition getInterviewTimeFieldDefinition(Integer cycleId) {
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        return fieldDefinitions.stream()
                .filter(field -> "期望的面试时间".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取"期望部门"字段定义
     */
    private ResumeFieldDefinition getExpectedDepartmentsFieldDefinition(Integer cycleId) {
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        return fieldDefinitions.stream()
                .filter(field -> "期望部门".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取用户期望的面试时间
     */
    private Map<Integer, List<String>> getUserPreferredTimes(List<Resume> resumes, Integer fieldId) {
        Map<Integer, List<String>> userPreferredTimes = new HashMap<>();
        
        for (Resume resume : resumes) {
            List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
            List<ResumeFieldValue> interviewTimeValues = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .collect(Collectors.toList());
            
            log.info("用户 {} 的期望面试时间字段值数量: {}", resume.getUserId(), interviewTimeValues.size());
            
            if (!interviewTimeValues.isEmpty()) {
                List<String> allPreferredTimes = new ArrayList<>();
                for (ResumeFieldValue interviewTimeValue : interviewTimeValues) {
                    log.info("用户 {} 的期望面试时间字段值: {}", resume.getUserId(), interviewTimeValue.getFieldValue());
                    try {
                        String fieldValue = interviewTimeValue.getFieldValue();
                        log.debug("解析用户 {} 的期望面试时间字段值: {}", resume.getUserId(), fieldValue);
                        
                        if (fieldValue != null && fieldValue.contains("\"first\"") && fieldValue.contains("\"second\"")) {
                            JsonNode jsonNode = objectMapper.readTree(fieldValue);
                            String first = jsonNode.has("first") ? jsonNode.get("first").asText() : null;
                            String second = jsonNode.has("second") ? jsonNode.get("second").asText() : null;
                            
                            log.debug("用户 {} 的first字段: {}, second字段: {}", resume.getUserId(), first, second);
                            
                            if (first != null && !first.isEmpty() && !"null".equals(first)) {
                                allPreferredTimes.add(first);
                            }
                            if (second != null && !second.isEmpty() && !"null".equals(second)) {
                                allPreferredTimes.add(second);
                            }
                        } else {
                            List<String> preferredTimes = objectMapper.readValue(fieldValue, new TypeReference<List<String>>() {});
                            allPreferredTimes.addAll(preferredTimes);
                        }
                        
                        log.debug("用户 {} 添加的期望时间: {}", resume.getUserId(), allPreferredTimes);
                    } catch (Exception e) {
                        log.warn("解析用户 {} 的期望面试时间失败: {}", resume.getUserId(),
                                interviewTimeValue.getFieldValue(), e);
                    }
                }
                log.info("用户 {} 解析后的所有期望面试时间: {}", resume.getUserId(), allPreferredTimes);
                userPreferredTimes.put(resume.getUserId(), allPreferredTimes);
            }
        }
        
        log.info("总共解析了 {} 个用户的期望面试时间", userPreferredTimes.size());
        return userPreferredTimes;
    }
    
    /**
     * 获取用户期望的部门
     */
    private Map<Integer, List<String>> getUserPreferredDepartments(List<Resume> resumes, Integer fieldId) {
        Map<Integer, List<String>> userPreferredDepartments = new HashMap<>();
        
        for (Resume resume : resumes) {
            List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
            List<ResumeFieldValue> expectedDepartmentsValues = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .collect(Collectors.toList());
            
            log.info("用户 {} 的期望部门字段值数量: {}", resume.getUserId(), expectedDepartmentsValues.size());
            
            if (!expectedDepartmentsValues.isEmpty()) {
                List<String> allPreferredDepartments = new ArrayList<>();
                for (ResumeFieldValue expectedDepartmentsValue : expectedDepartmentsValues) {
                    log.info("用户 {} 的期望部门字段值: {}", resume.getUserId(), expectedDepartmentsValue.getFieldValue());
                    try {
                        List<String> preferredDepartments = objectMapper.readValue(
                                expectedDepartmentsValue.getFieldValue(),
                                new TypeReference<List<String>>() {});
                        allPreferredDepartments.addAll(preferredDepartments);
                    } catch (Exception e) {
                        log.warn("解析用户 {} 的期望部门失败: {}", resume.getUserId(),
                                expectedDepartmentsValue.getFieldValue(), e);
                    }
                }
                log.info("用户 {} 解析后的所有期望部门: {}", resume.getUserId(), allPreferredDepartments);
                userPreferredDepartments.put(resume.getUserId(), allPreferredDepartments);
            }
        }
        
        return userPreferredDepartments;
    }
    
    /**
     * 为单个日期生成面试时间槽
    private List<LocalDateTime> generateTimeSlotsForSingleDay(LocalDate day) {
        List<LocalDateTime> timeSlots = new ArrayList<>();
        
        // 生成上午时间段 (9:00-11:00)
        for (LocalTime time = MORNING_START; time.isBefore(MORNING_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
            timeSlots.add(LocalDateTime.of(day, time));
        }
        
        // 生成下午时间段 (13:00-17:00)
        for (LocalTime time = AFTERNOON_START; time.isBefore(AFTERNOON_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
            timeSlots.add(LocalDateTime.of(day, time));
        }
        
        // 生成晚上时间段 (19:00-21:00)
        for (LocalTime time = EVENING_START; time.isBefore(EVENING_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
            timeSlots.add(LocalDateTime.of(day, time));
        }

        return timeSlots;
    }*/

    //生成面试时间槽
    private List<LocalDateTime> generateTimeSlots(Integer cycleId) {
        List<LocalDateTime> timeSlots = new ArrayList<>();

        // 获取该周期下所有可用的面试时段
        List<InterviewSlot> allSlots = interviewSlotService.getAvailableSlotsByCycleId(cycleId);

        // 按日期分组处理
        Map<LocalDate, List<InterviewSlot>> slotsByDate = allSlots.stream()
                .collect(Collectors.groupingBy(InterviewSlot::getInterviewDate));

        // 为每一天生成时间槽
        for (Map.Entry<LocalDate, List<InterviewSlot>> entry : slotsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<InterviewSlot> daySlots = entry.getValue();

            // 为当天的每个时段生成时间槽
            for (InterviewSlot slot : daySlots) {
                LocalTime currentTime = slot.getStartTime();
                while (currentTime.isBefore(slot.getEndTime()) || currentTime.equals(slot.getEndTime())) {
                    timeSlots.add(LocalDateTime.of(date, currentTime));
                    currentTime = currentTime.plusMinutes(INTERVIEW_DURATION);
                    if (currentTime.isAfter(slot.getEndTime())) {
                        break;
                    }
                }
            }
        }

        return timeSlots;
    }




    /**
     * 初始化各部门面试时间槽可用性
     */
    private Map<String, Map<LocalDateTime, Boolean>> initializeDepartmentSlotAvailability(
            List<LocalDateTime> timeSlots, Map<Integer, List<String>> userPreferredDepartments) {
        Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability = new HashMap<>();
        
        // 获取所有用户期望的部门
        Set<String> allDepartments = new HashSet<>();
        for (List<String> departments : userPreferredDepartments.values()) {
            allDepartments.addAll(departments);
        }
        
        // 为每个部门初始化时间槽可用性
        for (String department : allDepartments) {
            Map<LocalDateTime, Boolean> slotAvailability = new HashMap<>();
            for (LocalDateTime slot : timeSlots) {
                slotAvailability.put(slot, true);
            }
            departmentSlotAvailability.put(department, slotAvailability);
        }
        
        // 默认添加一些常见部门
        String[] defaultDepartments = {"技术部", "媒体部", "项目部", "综合部"};
        for (String department : defaultDepartments) {
            if (!departmentSlotAvailability.containsKey(department)) {
                Map<LocalDateTime, Boolean> slotAvailability = new HashMap<>();
                for (LocalDateTime slot : timeSlots) {
                    slotAvailability.put(slot, true);
                }
                departmentSlotAvailability.put(department, slotAvailability);
            }
        }
        
        return departmentSlotAvailability;
    }
    
    /**
     * 使用三层排序策略对候选人进行排序
     */
    private List<CandidateInfo> sortCandidatesByDepartmentTimeGroup(
            List<CandidateInfo> candidates,
            Map<Integer, List<String>> userPreferredTimes,
            Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        log.info("开始使用三层排序策略对 {} 个候选人进行排序", candidates.size());
        
        // 第一层：按(部门,时间段)分组
        Map<String, DepartmentTimeGroup> groups = createDepartmentTimeGroups(candidates);
        
        // 第二层：计算每组的优先级
        calculateGroupPriorities(groups, departmentSlotAvailability);
        
        // 第三层：组内排序
        sortCandidatesWithinGroups(groups, userPreferredTimes, departmentSlotAvailability);
        
        // 按组优先级重新排列候选人
        return flattenGroupsToSortedList(groups);
    }
    
    /**
     * 创建部门-时间段组合
     */
    private Map<String, DepartmentTimeGroup> createDepartmentTimeGroups(List<CandidateInfo> candidates) {
        Map<String, DepartmentTimeGroup> groups = new HashMap<>();
        
        for (CandidateInfo candidate : candidates) {
            String department = candidate.firstDepartment;
            
            // 为每个期望时间段创建组合
            for (String timeSlot : candidate.preferredTimes) {
                String groupKey = department + "|" + timeSlot;
                
                DepartmentTimeGroup group = groups.computeIfAbsent(groupKey,
                        k -> new DepartmentTimeGroup(department, timeSlot));
                group.addCandidate(candidate);
            }
        }
        
        log.info("创建了 {} 个部门-时间段组合", groups.size());
        return groups;
    }
    
    /**
     * 计算每组的优先级
     */
    private void calculateGroupPriorities(Map<String, DepartmentTimeGroup> groups,
                                          Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        // 找到最大组大小用于归一化
        int maxGroupSize = groups.values().stream().mapToInt(DepartmentTimeGroup::getSize).max().orElse(1);
        
        for (DepartmentTimeGroup group : groups.values()) {
            double groupSizeWeight = (double) group.getSize() / maxGroupSize;
            
            // 计算时间稀缺性
            double scarcityScore = calculateTimeSlotScarcity(group.getTimeSlot(), group.getDepartment(), departmentSlotAvailability);
            
            // 组优先级 = 组大小权重 * 0.7 + 时间稀缺性 * 0.3
            double groupPriority = groupSizeWeight * 0.7 + scarcityScore * 0.3;
            
            group.setGroupPriority(groupPriority);
        }
    }
    
    /**
     * 计算时间段的稀缺性分数
     */
    private double calculateTimeSlotScarcity(String timeSlot, String department,
                                             Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return 0.0;
        }
        
        // 计算该时间段的可用时间槽数量
        int availableSlots = 0;
        for (Map.Entry<LocalDateTime, Boolean> entry : slotAvailability.entrySet()) {
            if (entry.getValue() && isSlotMatchPreference(entry.getKey(), timeSlot)) {
                availableSlots++;
            }
        }
        
        // 稀缺性分数：可用时间槽越少，稀缺性越高
        if (availableSlots == 0) {
            return 1.0;
        }
        
        return Math.min(1.0, 10.0 / availableSlots);
    }
    
    /**
     * 组内排序
     */
    private void sortCandidatesWithinGroups(Map<String, DepartmentTimeGroup> groups,
                                            Map<Integer, List<String>> userPreferredTimes,
                                            Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        for (DepartmentTimeGroup group : groups.values()) {
            List<CandidateInfo> candidates = group.getCandidates();
            // 简单按用户ID排序
            candidates.sort(Comparator.comparing(c -> c.user.getUserId()));
        }
    }
    
    /**
     * 将分组后的候选人按组优先级重新排列为单一列表
     */
    private List<CandidateInfo> flattenGroupsToSortedList(Map<String, DepartmentTimeGroup> groups) {
        List<CandidateInfo> sortedCandidates = new ArrayList<>();
        Set<Integer> addedUserIds = new HashSet<>();
        
        // 按组优先级降序排列组
        List<DepartmentTimeGroup> sortedGroups = groups.values().stream()
                .sorted((g1, g2) -> Double.compare(g2.getGroupPriority(), g1.getGroupPriority()))
                .collect(Collectors.toList());
        
        // 按优先级顺序添加候选人
        for (DepartmentTimeGroup group : sortedGroups) {
            for (CandidateInfo candidate : group.getCandidates()) {
                if (!addedUserIds.contains(candidate.user.getUserId())) {
                    sortedCandidates.add(candidate);
                    addedUserIds.add(candidate.user.getUserId());
                }
            }
        }
        
        log.info("三层排序完成，最终排序结果包含 {} 个候选人", sortedCandidates.size());
        return sortedCandidates;
    }
    
    /**
     * 尝试为用户分配面试时间
     */
    private boolean tryAssignInterviewTime(User user, Resume resume, List<String> preferredTimes, String department,
                                           Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability,
                                           List<AutoAssignInterviewResponseDTO.AssignmentDetailDTO> assignmentDetails,
                                           ClassroomAssigner classroomAssigner,
                                           Map<Integer, List<String>> userPreferredDepartments,
                                           List<InterviewSchedule> schedulesToSave) {
        
        if (preferredTimes == null || preferredTimes.isEmpty()) {
            return false;
        }
        
        log.info("开始为用户 {} 分配面试时间，期望时间: {}", user.getUsername(), preferredTimes);
        
        // 按照用户期望的时间顺序尝试分配
        for (String preferredTime : preferredTimes) {
            log.info("尝试为用户 {} 分配时间: {}", user.getUsername(), preferredTime);
            
            // 检查该时间段是否有空位且有可用教室
            if (hasAvailableSlotAndClassroom(preferredTime, department, departmentSlotAvailability, classroomAssigner)) {
                log.info("用户 {} 的时间段 {} 有空位且有可用教室，正在分配", user.getUsername(), preferredTime);
                
                // 如果有空位且有教室，则分配时间
                LocalDateTime assignedSlot = findAndReserveSlot(preferredTime, department, departmentSlotAvailability);
                if (assignedSlot != null) {
                    // 分配教室
                    String classroom = classroomAssigner.assignClassroom(assignedSlot);
                    if (classroom != null) {
                        // 成功分配时间和教室
                        String period = getTimePeriod(assignedSlot.toLocalTime());
                        log.info("成功为用户 {} 分配面试时间: {} 教室: {}", user.getUsername(), assignedSlot, classroom);
                        
                        String name = getResumeName(resume);
                        String email = getResumeEmail(resume);
                        String major = getResumeMajor(resume);
                        String grade = getResumeGrade(resume);
                        String preferredDepartments = getFormattedPreferredDepartments(user.getUserId(), userPreferredDepartments);
                        String preferredTimesStr = String.join(", ", preferredTimes);

                        // 旧版算法预留的时间粒度可能不一定等于真实 slot start_time。
                        // 但这里至少尝试用（cycleId + assignedSlot.start_time）反查 slot_id，消除 TODO。
                        InterviewSlot matchedSlot = findInterviewSlotByCycleAndStartTime(resume.getCycleId(), assignedSlot);
                        Integer slotId = matchedSlot != null ? matchedSlot.getSlotId() : null;
                        
                        // 创建分配详情
                        AutoAssignInterviewResponseDTO.AssignmentDetailDTO detail = new AutoAssignInterviewResponseDTO.AssignmentDetailDTO();
                        detail.setUserId(user.getUserId());
                        detail.setUsername(user.getUsername());
                        detail.setName(name);
                        detail.setEmail(email);
                        detail.setSlotId(slotId);
                        detail.setInterviewTime(assignedSlot);
                        detail.setLocation("教室" + classroom);
                        detail.setPeriod(period);
                        detail.setDepartment(department);
                        detail.setClassroom(classroom);
                        detail.setPreferredDepartments(preferredDepartments);
                        detail.setPreferredTimes(preferredTimesStr);
                        
                        assignmentDetails.add(detail);
                        
                        // 创建面试安排实体
                        InterviewSchedule schedule = new InterviewSchedule()
                                .setResumeId(resume.getResumeId())
                                .setUserId(user.getUserId())
                                .setCycleId(resume.getCycleId())
                                .setSlotId(slotId)
                                .setInterviewTime(assignedSlot)
                                .setStatus(1) // 已安排
                                .setNotes("自动分配 - " + period + " - " + classroom)
                                .setSyncStatus(0) // 未同步
                                .setNotifStatus(0); // 未通知
                        
                        schedulesToSave.add(schedule);
                        
                        return true;
                    } else {
                        // 没有可用教室，需要释放时间槽
                        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
                        if (slotAvailability != null) {
                            slotAvailability.put(assignedSlot, true);
                        }
                        log.warn("用户 {} 的时间段 {} 没有可用教室，释放时间槽", user.getUsername(), preferredTime);
                    }
                }
            } else {
                log.info("用户 {} 的时间段 {} 没有空位或没有可用教室，尝试下一个时间段", user.getUsername(), preferredTime);
            }
        }
        
        log.info("用户 {} 的所有期望时间段都没有空位或没有可用教室，无法分配", user.getUsername());
        return false;
    }

    /**
     * 旧版算法用 localDateTime 粒度预留时刻，尽量反查到 interview_slot 的 slotId。
     */
    private InterviewSlot findInterviewSlotByCycleAndStartTime(Integer cycleId, LocalDateTime assignedSlot) {
        if (cycleId == null || assignedSlot == null) {
            return null;
        }
        List<InterviewSlot> allSlots = interviewSlotService.getAllSlotsByCycleId(cycleId);
        if (allSlots == null || allSlots.isEmpty()) {
            return null;
        }

        LocalDate date = assignedSlot.toLocalDate();
        LocalTime startTime = assignedSlot.toLocalTime();
        return allSlots.stream()
                .filter(s -> date.equals(s.getInterviewDate()))
                .filter(s -> startTime.equals(s.getStartTime()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取时间段描述
     */
    private String getTimePeriod(LocalTime timeOfDay) {
        if (!timeOfDay.isBefore(LocalTime.of(6, 0)) && timeOfDay.isBefore(LocalTime.of(12, 0))) {
            return "上午";
        } else if (!timeOfDay.isBefore(LocalTime.of(12, 0)) && timeOfDay.isBefore(LocalTime.of(18, 0))) {
            return "下午";
        } else if (!timeOfDay.isBefore(LocalTime.of(18, 0)) && timeOfDay.isBefore(LocalTime.of(22, 0))) {
            return "晚上";
        } else {
            return "未知";
        }
    }
    
    /**
     * 检查指定时间段是否还有空位且有可用教室
     */
    private boolean hasAvailableSlotAndClassroom(String preferredTime, String department,
                                                 Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability,
                                                 ClassroomAssigner classroomAssigner) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return false;
        }
        
        // 获取所有可用的时间槽
        List<LocalDateTime> availableSlots = slotAvailability.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        // 检查是否有符合期望时间段的时间槽且有可用教室
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime) && classroomAssigner.hasAvailableClassroom(slotTime)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 根据用户期望的时间段查找并预留时间槽
     */
    private LocalDateTime findAndReserveSlot(String preferredTime, String department,
                                             Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return null;
        }
        
        // 获取所有可用的时间槽
        List<LocalDateTime> availableSlots = slotAvailability.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        // 根据期望时间段筛选合适的时间槽
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime)) {
                // 预留该时间槽
                slotAvailability.put(slotTime, false);
                log.info("为部门 {} 预留时间槽: {}", department, slotTime);
                return slotTime;
            }
        }
        
        return null;
    }
    
    /**
     * Legacy overload for旧版算法中的调用。
     * 新版 auto-assign 已不再依赖该方法；为了保证编译通过，这里返回 false。
     */
    private boolean isSlotMatchPreference(LocalDateTime slotTime, String preferredTime) {
        return false;
    }

    /**
     * 检查时间槽是否符合用户的期望时间段
     */
    private boolean isSlotMatchPreference(LocalDateTime slotTime, String preferredTime, Integer cycleId) {
        String[] parts = preferredTime.split(" ");
        if (parts.length < 3 || !"Day".equals(parts[0])) {
            log.warn("无效的期望时间格式: {}", preferredTime);
            return false;
        }

        try {
            int dayNumber = Integer.parseInt(parts[1]);
            String period = parts[2];

            // 获取该招募周期的所有面试时段
            List<InterviewSlot> allSlots = interviewSlotService.getAvailableSlotsByCycleId(cycleId);

            // 按日期分组
            Map<LocalDate, List<InterviewSlot>> slotsByDate = allSlots.stream()
                    .collect(Collectors.groupingBy(InterviewSlot::getInterviewDate));

            // 获取第N天的日期（按日期排序后的第N天）
            List<LocalDate> sortedDates = slotsByDate.keySet().stream()
                    .sorted()
                    .collect(Collectors.toList());

            if (dayNumber > sortedDates.size()) {
                log.warn("期望的天数 {} 超出可用日期范围", dayNumber);
                return false;
            }

            LocalDate targetDate = sortedDates.get(dayNumber - 1);

            // 检查该时间槽是否在目标日期且属于期望的时段
            if (slotTime.toLocalDate().equals(targetDate)) {
                // 获取该日期的所有时段
                List<InterviewSlot> daySlots = slotsByDate.get(targetDate);

                // 判断时间槽是否在任何时段内
                for (InterviewSlot slot : daySlots) {
                    if (!slotTime.toLocalTime().isBefore(slot.getStartTime()) &&
                            slotTime.toLocalTime().isBefore(slot.getEndTime())) {
                        // 判断该时段属于哪个大时段
                        if (isInExpectedPeriod(slotTime.toLocalTime(), period, daySlots)) {
                            return true;
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            log.warn("解析期望时间失败: {}", preferredTime, e);
            return false;
        }

        return false;
    }

    /**
     * 判断时间是否在期望的时段内
     */
    private boolean isInExpectedPeriod(LocalTime time, String expectedPeriod, List<InterviewSlot> daySlots) {
        switch (expectedPeriod) {
            case "上午":
                // 上午时段：通常在12点前结束
                return time.isBefore(LocalTime.of(12, 0));
            case "下午":
                // 下午时段：通常在12点后，18点前
                return !time.isBefore(LocalTime.of(12, 0)) && time.isBefore(LocalTime.of(18, 0));
            case "晚上":
                // 晚上时段：通常在18点后
                return !time.isBefore(LocalTime.of(18, 0));
            default:
                return false;
        }
    }


    // 内部类定义
    
    /**
     * 候选人信息类
     */
    private static class CandidateInfo {
        private final User user;
        private final List<String> preferredTimes;
        private final List<String> preferredDepartments;
        private final String firstDepartment;
        private final Resume resume;
        
        public CandidateInfo(User user, List<String> preferredTimes, List<String> preferredDepartments, 
                           String firstDepartment, Resume resume) {
            this.user = user;
            this.preferredTimes = preferredTimes;
            this.preferredDepartments = preferredDepartments;
            this.firstDepartment = firstDepartment;
            this.resume = resume;
        }
    }
    
    /**
     * 部门-时间段组合类
     */
    private static class DepartmentTimeGroup {
        private final String department;
        private final String timeSlot;
        private final List<CandidateInfo> candidates;
        private double groupPriority;
        
        public DepartmentTimeGroup(String department, String timeSlot) {
            this.department = department;
            this.timeSlot = timeSlot;
            this.candidates = new ArrayList<>();
            this.groupPriority = 0.0;
        }
        
        public void addCandidate(CandidateInfo candidate) {
            this.candidates.add(candidate);
        }
        
        public String getDepartment() { return department; }
        public String getTimeSlot() { return timeSlot; }
        public List<CandidateInfo> getCandidates() { return candidates; }
        public double getGroupPriority() { return groupPriority; }
        public void setGroupPriority(double groupPriority) { this.groupPriority = groupPriority; }
        public int getSize() { return candidates.size(); }
    }
    
    /**
     * 教室分配管理器
     */
    private static class ClassroomAssigner {
        private final Map<String, Integer> timeSlotClassroomCounter = new HashMap<>();
        private static final String[] CLASSROOMS = {"1", "2", "3"};
        
        public String assignClassroom(LocalDateTime interviewTime) {
            String timeKey = interviewTime.toString();
            int currentCount = timeSlotClassroomCounter.getOrDefault(timeKey, 0);
            
            if (currentCount < CLASSROOMS.length) {
                timeSlotClassroomCounter.put(timeKey, currentCount + 1);
                return CLASSROOMS[currentCount];
            }
            
            return null;
        }
        
        public boolean hasAvailableClassroom(LocalDateTime interviewTime) {
            String timeKey = interviewTime.toString();
            int currentCount = timeSlotClassroomCounter.getOrDefault(timeKey, 0);
            return currentCount < CLASSROOMS.length;
        }
    }
}