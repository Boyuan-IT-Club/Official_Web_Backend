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
import com.fasterxml.jackson.databind.JsonNode;
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
        List<Resume> allResumes = resumeService.getAllResumesByCycleId(cycleId);
        // 只处理已提交的简历(status >= 2)
        List<Resume> resumes = allResumes.stream()
                .filter(resume -> resume.getStatus() != null && resume.getStatus() >= 2)
                .collect(Collectors.toList());
        logger.info("获取到 {} 份简历，其中已提交 {} 份", allResumes.size(), resumes.size());
        
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
        
        // 初始化各部门面试时间槽 (将Day1设置为9月27日，Day2设置为9月28日)
        LocalDate day1 = LocalDate.of(2025, 9, 27);
        LocalDate day2 = LocalDate.of(2025, 9, 28);
        List<LocalDateTime> timeSlots = generateTimeSlotsForSpecificDays(day1, day2);
        Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability = initializeDepartmentSlotAvailability(timeSlots, userPreferredDepartments);
        
        // 使用优化的分配策略分配面试时间
        return assignInterviewsWithOptimization(resumes, userPreferredTimes, userPreferredDepartments, departmentSlotAvailability);
    }
    
    /**
     * 使用优化策略分配面试时间，以满足更多人的偏好
     */
    private InterviewAssignmentResultDTO assignInterviewsWithOptimization(
            List<Resume> resumes,
            Map<Integer, List<String>> userPreferredTimes,
            Map<Integer, List<String>> userPreferredDepartments,
            Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        // 创建候选人列表，包含他们的偏好信息
        List<CandidateInfo> candidates = new ArrayList<>();
        List<InterviewAssignmentResultDTO.UnassignedUserDTO> unassignedUsers = new ArrayList<>();
        List<InterviewAssignmentResultDTO.NoPreferenceUserDTO> noPreferenceUsers = new ArrayList<>();
        
        for (Resume resume : resumes) {
            User user = userService.getUserById(resume.getUserId());
            if (user == null) {
                logger.warn("简历 {} 对应的用户 {} 不存在", resume.getResumeId(), resume.getUserId());
                continue;
            }
            
            List<String> preferredTimes = userPreferredTimes.getOrDefault(resume.getUserId(), new ArrayList<>());
            List<String> preferredDepartments = userPreferredDepartments.getOrDefault(resume.getUserId(), new ArrayList<>());
            
            // 添加调试日志
            logger.info("用户 {} 的期望面试时间: {}", user.getUsername(), preferredTimes);
            logger.info("用户 {} 的期望部门: {}", user.getUsername(), preferredDepartments);
            
            // 如果用户没有填写期望面试时间，则加入未填写期望面试时间列表
            if (preferredTimes.isEmpty()) {
                logger.info("用户 {} 没有填写期望面试时间，加入未填写期望面试时间列表", user.getUsername());
                // 从简历中获取姓名而不是从用户表中获取
                String name = getResumeName(resume);
                noPreferenceUsers.add(new InterviewAssignmentResultDTO.NoPreferenceUserDTO(
                        user.getUserId(), user.getUsername(), name));
                continue;
            }
            
            // 如果用户没有填写期望部门，则加入未填写期望面试时间列表
            if (preferredDepartments.isEmpty()) {
                logger.info("用户 {} 没有填写期望部门，加入未填写期望面试时间列表", user.getUsername());
                // 从简历中获取姓名而不是从用户表中获取
                String name = getResumeName(resume);
                noPreferenceUsers.add(new InterviewAssignmentResultDTO.NoPreferenceUserDTO(
                        user.getUserId(), user.getUsername(), name));
                continue;
            }
            
            String firstDepartment = preferredDepartments.get(0);
            candidates.add(new CandidateInfo(user, preferredTimes, preferredDepartments, firstDepartment, resume));
        }
        
        // 按照偏好满足度排序候选人（偏好越多的候选人优先级越高）
        candidates.sort((c1, c2) -> {
            // 优先考虑偏好时间更多的候选人
            int timePrefCompare = Integer.compare(c2.preferredTimes.size(), c1.preferredTimes.size());
            if (timePrefCompare != 0) {
                return timePrefCompare;
            }
            // 如果偏好时间数量相同，则考虑偏好部门数量
            return Integer.compare(c2.preferredDepartments.size(), c1.preferredDepartments.size());
        });
        
        // 分配面试时间
        List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignedInterviews = new ArrayList<>();
        
        for (CandidateInfo candidate : candidates) {
            User user = candidate.user;
            Resume resume = candidate.resume;
            List<String> preferredTimes = candidate.preferredTimes;
            String department = candidate.firstDepartment;
            
            // 尝试分配面试时间
            boolean assigned = tryAssignInterviewTime(
                    user, resume, preferredTimes, department, departmentSlotAvailability, assignedInterviews);
            
            // 如果无法分配（所有时间段都满了），则加入未分配列表
            if (!assigned) {
                String preferredTimesStr = String.join(", ", preferredTimes);
                String preferredDepartmentsStr = String.join(", ", candidate.preferredDepartments);
                logger.info("用户 {} 未被分配，期望时间: {}，期望部门: {}", user.getUsername(), preferredTimesStr, preferredDepartmentsStr);
                // 从简历中获取姓名而不是从用户表中获取
                String name = getResumeName(resume);
                unassignedUsers.add(new InterviewAssignmentResultDTO.UnassignedUserDTO(
                        user.getUserId(), user.getUsername(), name, preferredTimesStr, preferredDepartmentsStr));
            } else {
                logger.info("用户 {} 已成功分配面试时间", user.getUsername());
            }
        }
        
        logger.info("面试时间分配完成，已分配 {} 人，未分配 {} 人，未填写期望 {} 人", 
                assignedInterviews.size(), unassignedUsers.size(), noPreferenceUsers.size());
        return new InterviewAssignmentResultDTO(assignedInterviews, unassignedUsers, noPreferenceUsers);
    }
    
    /**
     * 从简历中获取姓名字段值
     * @param resume 简历对象
     * @return 姓名字段值，如果找不到则返回用户表中的姓名
     */
    private String getResumeName(Resume resume) {
        // 获取简历中的所有字段值
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        
        // 获取当前周期的字段定义
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());
        
        // 查找姓名字段定义
        ResumeFieldDefinition nameFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "姓名".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
        
        // 如果找到了姓名字段定义，则查找对应的字段值
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
        
        // 如果简历中没有姓名字段或为空，则使用用户表中的姓名
        User user = userService.getUserById(resume.getUserId());
        return user != null ? user.getName() : "";
    }
    
    /**
     * 候选人信息类，用于存储分配过程中的相关信息
     */
    private static class CandidateInfo {
        private final User user;
        private final List<String> preferredTimes;
        private final List<String> preferredDepartments;
        private final String firstDepartment;
        private final Resume resume; // 添加简历字段
        
        public CandidateInfo(User user, List<String> preferredTimes, List<String> preferredDepartments, String firstDepartment, Resume resume) {
            this.user = user;
            this.preferredTimes = preferredTimes;
            this.preferredDepartments = preferredDepartments;
            this.firstDepartment = firstDepartment;
            this.resume = resume;
        }
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
            // 获取所有匹配的字段值
            List<ResumeFieldValue> interviewTimeValues = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .collect(Collectors.toList());
            
            logger.info("用户 {} 的期望面试时间字段值数量: {}", resume.getUserId(), interviewTimeValues.size());
            
            if (!interviewTimeValues.isEmpty()) {
                List<String> allPreferredTimes = new ArrayList<>();
                for (ResumeFieldValue interviewTimeValue : interviewTimeValues) {
                    logger.info("用户 {} 的期望面试时间字段值: {}", resume.getUserId(), interviewTimeValue.getFieldValue());
                    try {
                        // 检查是否是包含first和second字段的JSON对象格式
                        String fieldValue = interviewTimeValue.getFieldValue();
                        if (fieldValue != null && fieldValue.contains("\"first\"") && fieldValue.contains("\"second\"")) {
                            // 解析包含first和second字段的JSON对象
                            JsonNode jsonNode = objectMapper.readTree(fieldValue);
                            String first = jsonNode.get("first").asText();
                            String second = jsonNode.get("second").asText();
                            
                            // 添加非空的时间选项
                            if (first != null && !first.isEmpty() && !"null".equals(first)) {
                                allPreferredTimes.add(first);
                            }
                            if (second != null && !second.isEmpty() && !"null".equals(second)) {
                                allPreferredTimes.add(second);
                            }
                        } else {
                            // 解析普通的JSON数组格式（向后兼容）
                            List<String> preferredTimes = objectMapper.readValue(fieldValue, new TypeReference<List<String>>() {});
                            allPreferredTimes.addAll(preferredTimes);
                        }
                    } catch (Exception e) {
                        logger.warn("解析用户 {} 的期望面试时间失败: {}", resume.getUserId(), 
                                interviewTimeValue.getFieldValue(), e);
                    }
                }
                logger.info("用户 {} 解析后的所有期望面试时间: {}", resume.getUserId(), allPreferredTimes);
                userPreferredTimes.put(resume.getUserId(), allPreferredTimes);
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
            // 获取所有匹配的字段值
            List<ResumeFieldValue> expectedDepartmentsValues = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .collect(Collectors.toList());
            
            logger.info("用户 {} 的期望部门字段值数量: {}", resume.getUserId(), expectedDepartmentsValues.size());
            
            if (!expectedDepartmentsValues.isEmpty()) {
                List<String> allPreferredDepartments = new ArrayList<>();
                for (ResumeFieldValue expectedDepartmentsValue : expectedDepartmentsValues) {
                    logger.info("用户 {} 的期望部门字段值: {}", resume.getUserId(), expectedDepartmentsValue.getFieldValue());
                    try {
                        // 解析JSON数组
                        List<String> preferredDepartments = objectMapper.readValue(
                                expectedDepartmentsValue.getFieldValue(), 
                                new TypeReference<List<String>>() {});
                        allPreferredDepartments.addAll(preferredDepartments);
                    } catch (Exception e) {
                        logger.warn("解析用户 {} 的期望部门失败: {}", resume.getUserId(), 
                                expectedDepartmentsValue.getFieldValue(), e);
                    }
                }
                logger.info("用户 {} 解析后的所有期望部门: {}", resume.getUserId(), allPreferredDepartments);
                userPreferredDepartments.put(resume.getUserId(), allPreferredDepartments);
            }
        }
        
        return userPreferredDepartments;
    }
    
    /**
     * 为特定日期生成面试时间槽 (Day1为9月27日，Day2为9月28日)
     */
    private List<LocalDateTime> generateTimeSlotsForSpecificDays(LocalDate day1, LocalDate day2) {
        List<LocalDateTime> timeSlots = new ArrayList<>();
        
        // 为指定的两天生成面试时间槽
        LocalDate[] dates = {day1, day2};
        for (LocalDate date : dates) {
            // 生成上午时间段 (9:00-11:00)
            for (LocalTime time = MORNING_START; !time.equals(MORNING_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
                timeSlots.add(LocalDateTime.of(date, time));
            }
            
            // 生成下午时间段 (13:00-17:00)
            for (LocalTime time = AFTERNOON_START; !time.equals(AFTERNOON_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
                timeSlots.add(LocalDateTime.of(date, time));
            }
        }
        
        return timeSlots;
    }
    
    /**
     * 生成面试时间槽（原始方法，基于招募周期）
     */
    private List<LocalDateTime> generateTimeSlots(LocalDate startDate, LocalDate endDate) {
        List<LocalDateTime> timeSlots = new ArrayList<>();
        
        // 为招募周期的前两天生成面试时间槽
        for (int dayOffset = 0; dayOffset < 2; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            
            // 生成上午时间段 (9:00-11:00)
            for (LocalTime time = MORNING_START; !time.equals(MORNING_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
                timeSlots.add(LocalDateTime.of(date, time));
            }
            
            // 生成下午时间段 (13:00-17:00)
            for (LocalTime time = AFTERNOON_START; !time.equals(AFTERNOON_END); time = time.plusMinutes(INTERVIEW_DURATION)) {
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
     * 尝试为用户分配面试时间
     */
    private boolean tryAssignInterviewTime(User user, Resume resume, List<String> preferredTimes, String department,
                                         Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability,
                                         List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignedInterviews) {
        // 如果用户没有填写期望面试时间，则不分配面试时间
        if (preferredTimes == null || preferredTimes.isEmpty()) {
            return false;
        }
        
        logger.info("开始为用户 {} 分配面试时间，期望时间: {}", user.getUsername(), preferredTimes);
        
        // 按照用户期望的时间顺序尝试分配
        for (String preferredTime : preferredTimes) {
            logger.info("尝试为用户 {} 分配时间: {}", user.getUsername(), preferredTime);
            // 检查该时间段是否有空位
            if (hasAvailableSlot(preferredTime, department, departmentSlotAvailability)) {
                logger.info("用户 {} 的时间段 {} 有空位，正在分配", user.getUsername(), preferredTime);
                // 如果有空位，则分配时间
                LocalDateTime assignedSlot = findAndReserveSlot(preferredTime, department, departmentSlotAvailability);
                if (assignedSlot != null) {
                    // 成功分配时间
                    String period = assignedSlot.getHour() < 12 ? "上午" : "下午";
                    logger.info("成功为用户 {} 分配面试时间: {}", user.getUsername(), assignedSlot);
                    // 从简历中获取姓名而不是从用户表中获取
                    String name = getResumeName(resume);
                    assignedInterviews.add(new InterviewAssignmentResultDTO.AssignedInterviewDTO(
                            user.getUserId(), user.getUsername(), name, assignedSlot, period, department));
                    return true;
                }
            } else {
                logger.info("用户 {} 的时间段 {} 没有空位，尝试下一个时间段", user.getUsername(), preferredTime);
            }
        }
        
        // 如果用户选择的所有时间段都没有空位，则不分配时间
        logger.info("用户 {} 的所有期望时间段都没有空位，无法分配", user.getUsername());
        return false;
    }
    
    /**
     * 检查指定时间段是否还有空位
     */
    private boolean hasAvailableSlot(String preferredTime, String department,
                                   Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return false;
        }
        
        // 获取所有可用的时间槽
        List<LocalDateTime> availableSlots = slotAvailability.entrySet().stream()
                .filter(Map.Entry::getValue) // 只考虑可用的时间槽
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        logger.debug("部门 {} 的可用时间槽: {}", department, availableSlots);
        
        // 检查是否有符合期望时间段的时间槽
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime)) {
                logger.debug("找到匹配的时间槽: {} 对应偏好时间: {}", slotTime, preferredTime);
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
                .filter(Map.Entry::getValue) // 只考虑可用的时间槽
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        // 根据期望时间段筛选合适的时间槽
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime)) {
                // 预留该时间槽
                slotAvailability.put(slotTime, false);
                logger.info("为部门 {} 预留时间槽: {}", department, slotTime);
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
     * 严格按照用户选择的具体日期和时间段进行匹配
     */
    private boolean isSlotMatchPreference(LocalDateTime slotTime, String preferredTime) {
        String[] parts = preferredTime.split(" ");
        if (parts.length != 3 || !"Day".equals(parts[0])) {
            return false;
        }
        
        try {
            int dayNumber = Integer.parseInt(parts[1]);
            String period = parts[2]; // "上午" 或 "下午"
            
            // 根据面试时间槽的日期确定是第几天
            LocalDate day1 = LocalDate.of(2025, 9, 27);
            LocalDate day2 = LocalDate.of(2025, 9, 28);
            
            // 严格匹配具体日期和时间段
            if (dayNumber == 1 && slotTime.toLocalDate().equals(day1)) {
                // 检查时间段是否匹配
                LocalTime time = slotTime.toLocalTime();
                if ("上午".equals(period)) {
                    return !time.isBefore(MORNING_START) && time.isBefore(MORNING_END);
                } else if ("下午".equals(period)) {
                    return !time.isBefore(AFTERNOON_START) && time.isBefore(AFTERNOON_END);
                }
            } else if (dayNumber == 2 && slotTime.toLocalDate().equals(day2)) {
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