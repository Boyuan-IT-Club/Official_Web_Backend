package club.boyuan.official.service.impl;

import club.boyuan.official.dto.InterviewAssignmentResultDTO;
import club.boyuan.official.entity.RecruitmentCycle;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.entity.ResumeFieldValue;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IInterviewAssignmentService;
import club.boyuan.official.service.IRecruitmentCycleService;
import club.boyuan.official.service.IResumeFieldDefinitionService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.IUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试时间分配服务实现类
 */
@Service
@AllArgsConstructor
public class InterviewAssignmentServiceImpl implements IInterviewAssignmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(InterviewAssignmentServiceImpl.class);
    
    private final IResumeService resumeService;
    private final IRecruitmentCycleService recruitmentCycleService;
    private final IResumeFieldDefinitionService resumeFieldDefinitionService;
    private final IUserService userService;
    private final ObjectMapper objectMapper;
    // 面试时间段定义
    private static final LocalTime MORNING_START = LocalTime.of(9, 0);
    private static final LocalTime MORNING_END = LocalTime.of(11, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);
    private static final int INTERVIEW_DURATION = 10; // 面试时长10分钟
    
    @Override
    public InterviewAssignmentResultDTO assignInterviews(Integer cycleId) {
        logger.info("开始为招募周期ID {} 分配面试时间", cycleId);
        
        // 获取招募周期信息
        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            throw new IllegalArgumentException("招募周期不存在，ID: " + cycleId);
        }
        
        // 获取该周期下的所有简历
        List<Resume> resumes = resumeService.getAllResumesByCycleId(cycleId);
        logger.info("获取到 {} 份简历", resumes.size());
        
        // 获取"期望的面试时间"和"期望部门"字段定义
        ResumeFieldDefinition interviewTimeField = getInterviewTimeFieldDefinition(cycleId);
        if (interviewTimeField == null) {
            throw new IllegalStateException("未找到'期望的面试时间'字段定义");
        }
        
        ResumeFieldDefinition expectedDepartmentsField = getExpectedDepartmentsFieldDefinition(cycleId);
        if (expectedDepartmentsField == null) {
            throw new IllegalStateException("未找到'期望部门'字段定义");
        }
        
        // 获取所有用户的期望面试时间和期望部门
        Map<Integer, List<String>> userPreferredTimes = getUserPreferredTimes(resumes, interviewTimeField.getFieldId());
        Map<Integer, List<String>> userPreferredDepartments = getUserPreferredDepartments(resumes, expectedDepartmentsField.getFieldId());
        
        // 初始化各部门面试时间槽
        List<LocalDateTime> timeSlots = generateTimeSlots(cycle.getStartDate(), cycle.getEndDate());
        Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability = initializeDepartmentSlotAvailability(timeSlots, userPreferredDepartments);
        
        // 分配面试时间
        List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignedInterviews = new ArrayList<>();
        List<InterviewAssignmentResultDTO.UnassignedUserDTO> unassignedUsers = new ArrayList<>();
        
        for (Resume resume : resumes) {
            User user = userService.getUserById(resume.getUserId());
            if (user == null) {
                logger.warn("简历 {} 对应的用户 {} 不存在", resume.getResumeId(), resume.getUserId());
                continue;
            }
            
            List<String> preferredTimes = userPreferredTimes.getOrDefault(resume.getUserId(), new ArrayList<>());
            List<String> preferredDepartments = userPreferredDepartments.getOrDefault(resume.getUserId(), new ArrayList<>());
            
            // 获取第一志愿部门
            String firstDepartment = preferredDepartments.isEmpty() ? "未指定部门" : preferredDepartments.get(0);
            
            // 尝试分配面试时间
            boolean assigned = tryAssignInterviewTime(
                    user, preferredTimes, firstDepartment, departmentSlotAvailability, assignedInterviews);
            
            // 如果无法分配，则加入未分配列表
            if (!assigned) {
                String preferredTimesStr = String.join(", ", preferredTimes);
                String preferredDepartmentsStr = String.join(", ", preferredDepartments);
                unassignedUsers.add(new InterviewAssignmentResultDTO.UnassignedUserDTO(
                        user.getUserId(), user.getUsername(), user.getName(), preferredTimesStr, preferredDepartmentsStr));
            }
        }
        
        logger.info("面试时间分配完成，已分配 {} 人，未分配 {} 人", assignedInterviews.size(), unassignedUsers.size());
        
        return new InterviewAssignmentResultDTO(assignedInterviews, unassignedUsers);
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
            Optional<ResumeFieldValue> interviewTimeValue = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .findFirst();
            
            if (interviewTimeValue.isPresent()) {
                try {
                    // 解析JSON数组
                    List<String> preferredTimes = objectMapper.readValue(
                            interviewTimeValue.get().getFieldValue(), 
                            new TypeReference<List<String>>() {});
                    userPreferredTimes.put(resume.getUserId(), preferredTimes);
                } catch (Exception e) {
                    logger.warn("解析用户 {} 的期望面试时间失败: {}", resume.getUserId(), 
                            interviewTimeValue.get().getFieldValue(), e);
                }
            }
        }
        
        return userPreferredTimes;
    }
    
    /**
     * 获取用户期望的部门
     */
    private Map<Integer, List<String>> getUserPreferredDepartments(List<Resume> resumes, Integer fieldId) {
        Map<Integer, List<String>> userPreferredDepartments = new HashMap<>();
        
        for (Resume resume : resumes) {
            List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
            Optional<ResumeFieldValue> expectedDepartmentsValue = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .findFirst();
            
            if (expectedDepartmentsValue.isPresent()) {
                try {
                    // 解析JSON数组
                    List<String> preferredDepartments = objectMapper.readValue(
                            expectedDepartmentsValue.get().getFieldValue(), 
                            new TypeReference<List<String>>() {});
                    userPreferredDepartments.put(resume.getUserId(), preferredDepartments);
                } catch (Exception e) {
                    logger.warn("解析用户 {} 的期望部门失败: {}", resume.getUserId(), 
                            expectedDepartmentsValue.get().getFieldValue(), e);
                }
            }
        }
        
        return userPreferredDepartments;
    }
    
    /**
     * 生成面试时间槽
     */
    private List<LocalDateTime> generateTimeSlots(LocalDate startDate, LocalDate endDate) {
        List<LocalDateTime> timeSlots = new ArrayList<>();
        
        // 为招募周期的前两天生成面试时间槽
        for (int dayOffset = 0; dayOffset < 2; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            
            // 生成上午时间段 (9:00-11:00)
            for (LocalTime time = MORNING_START; time.isBefore(MORNING_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
                timeSlots.add(LocalDateTime.of(date, time));
            }
            
            // 生成下午时间段 (13:00-17:00)
            for (LocalTime time = AFTERNOON_START; time.isBefore(AFTERNOON_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
                timeSlots.add(LocalDateTime.of(date, time));
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
                slotAvailability.put(slot, true); // 初始时所有时间槽都可用
            }
            departmentSlotAvailability.put(department, slotAvailability);
        }
        
        // 默认添加一些常见部门
        String[] defaultDepartments = {"技术部", "产品部", "设计部", "运营部"};
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
     * 尝试为用户分配面试时间
     */
    private boolean tryAssignInterviewTime(User user, List<String> preferredTimes, String department,
                                         Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability,
                                         List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignedInterviews) {
        // 按照用户期望的时间顺序尝试分配
        for (String preferredTime : preferredTimes) {
            LocalDateTime assignedSlot = findAndReserveSlot(preferredTime, department, departmentSlotAvailability);
            if (assignedSlot != null) {
                // 成功分配时间
                String period = assignedSlot.getHour() < 12 ? "上午" : "下午";
                assignedInterviews.add(new InterviewAssignmentResultDTO.AssignedInterviewDTO(
                        user.getUserId(), user.getUsername(), user.getName(), assignedSlot, period, department));
                return true;
            }
        }
        
        // 如果按照用户期望的时间无法分配，则尝试分配到任何可用的时间
        LocalDateTime assignedSlot = findAnyAvailableSlot(department, departmentSlotAvailability);
        if (assignedSlot != null) {
            String period = assignedSlot.getHour() < 12 ? "上午" : "下午";
            assignedInterviews.add(new InterviewAssignmentResultDTO.AssignedInterviewDTO(
                    user.getUserId(), user.getUsername(), user.getName(), assignedSlot, period, department));
            return true;
        }
        
        return false; // 无法分配时间
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
                .filter(Map.Entry::getValue) // 只考虑可用的时间槽
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        // 根据期望时间段筛选合适的时间槽
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime)) {
                // 预留该时间槽
                slotAvailability.put(slotTime, false);
                return slotTime;
            }
        }
        
        return null;
    }
    
    /**
     * 查找任何可用的时间槽
     */
    private LocalDateTime findAnyAvailableSlot(String department,
                                             Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return null;
        }
        
        // 获取所有可用的时间槽
        Optional<LocalDateTime> availableSlot = slotAvailability.entrySet().stream()
                .filter(Map.Entry::getValue) // 只考虑可用的时间槽
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst();
        
        if (availableSlot.isPresent()) {
            // 预留该时间槽
            slotAvailability.put(availableSlot.get(), false);
            return availableSlot.get();
        }
        
        return null;
    }
    
    /**
     * 检查时间槽是否符合用户的期望时间段
     */
    private boolean isSlotMatchPreference(LocalDateTime slotTime, String preferredTime) {
        String[] parts = preferredTime.split(" ");
        if (parts.length != 3 || !"Day".equals(parts[0])) {
            return false;
        }
        
        try {
            int dayNumber = Integer.parseInt(parts[1]);
            String period = parts[2]; // "上午" 或 "下午"
            
            // 判断是第几天 (假设面试从招募周期开始日期算起)
            // 注意：这里简化处理，实际应该根据开始日期计算
            if (dayNumber == 1 || dayNumber == 2) {
                // 检查时间段是否匹配
                LocalTime time = slotTime.toLocalTime();
                if ("上午".equals(period)) {
                    return !time.isBefore(MORNING_START) && time.isBefore(MORNING_END);
                } else if ("下午".equals(period)) {
                    return !time.isBefore(AFTERNOON_START) && time.isBefore(AFTERNOON_END);
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        
        return false;
    }
}