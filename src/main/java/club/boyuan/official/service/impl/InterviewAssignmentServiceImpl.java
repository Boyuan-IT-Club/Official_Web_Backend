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
    private static final LocalTime EVENING_START = LocalTime.of(19, 0);
    private static final LocalTime EVENING_END = LocalTime.of(21, 0);
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
        
        // 初始化各部门面试时间槽 (将Day1设置为9月27日，仅有一天面试)
        LocalDate day1 = LocalDate.of(2025, 9, 27);
        List<LocalDateTime> timeSlots = generateTimeSlotsForSingleDay(day1);
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
                // 从简历中获取邮箱而不是从用户表中获取
                String email = getResumeEmail(resume);
                noPreferenceUsers.add(new InterviewAssignmentResultDTO.NoPreferenceUserDTO(
                        user.getUserId(), user.getUsername(), name, email));
                continue;
            }
            
            // 如果用户没有填写期望部门，则加入未填写期望面试时间列表
            if (preferredDepartments.isEmpty()) {
                logger.info("用户 {} 没有填写期望部门，加入未填写期望面试时间列表", user.getUsername());
                // 从简历中获取姓名而不是从用户表中获取
                String name = getResumeName(resume);
                // 从简历中获取邮箱而不是从用户表中获取
                String email = getResumeEmail(resume);
                noPreferenceUsers.add(new InterviewAssignmentResultDTO.NoPreferenceUserDTO(
                        user.getUserId(), user.getUsername(), name, email));
                continue;
            }
            
            String firstDepartment = preferredDepartments.get(0);
            candidates.add(new CandidateInfo(user, preferredTimes, preferredDepartments, firstDepartment, resume));
        }
        
        // 使用改进的三层排序策略：部门-时间段分组排序
        candidates = sortCandidatesByDepartmentTimeGroup(candidates, userPreferredTimes, departmentSlotAvailability);
        
        // 初始化教室分配管理器
        ClassroomAssigner classroomAssigner = new ClassroomAssigner();
        
        // 分配面试时间
        List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignedInterviews = new ArrayList<>();
        
        for (CandidateInfo candidate : candidates) {
            User user = candidate.user;
            Resume resume = candidate.resume;
            List<String> preferredTimes = candidate.preferredTimes;
            String department = candidate.firstDepartment;
            
            // 严格按照用户偏好分配面试时间，不使用降级策略
            boolean assigned = tryAssignInterviewTime(
                    user, resume, preferredTimes, department, departmentSlotAvailability, assignedInterviews, classroomAssigner);
            
            // 如果无法分配（所有时间段都满了），则加入未分配列表
            if (!assigned) {
                String preferredTimesStr = String.join(", ", preferredTimes);
                String preferredDepartmentsStr = String.join(", ", candidate.preferredDepartments);
                logger.info("用户 {} 未被分配，期望时间: {}，期望部门: {}", user.getUsername(), preferredTimesStr, preferredDepartmentsStr);
                // 从简历中获取姓名而不是从用户表中获取
                String name = getResumeName(candidate.resume);
                // 从简历中获取邮箱而不是从用户表中获取
                String email = getResumeEmail(candidate.resume);
                unassignedUsers.add(new InterviewAssignmentResultDTO.UnassignedUserDTO(
                        user.getUserId(), user.getUsername(), name, email, preferredTimesStr, preferredDepartmentsStr));
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
     * 从简历中获取邮箱字段值
     * @param resume 简历对象
     * @return 邮箱字段值，如果找不到则返回用户表中的邮箱
     */
    private String getResumeEmail(Resume resume) {
        // 获取简历中的所有字段值
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        
        // 获取当前周期的字段定义
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());
        
        // 查找邮箱字段定义
        ResumeFieldDefinition emailFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "邮箱".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
        
        // 如果找到了邮箱字段定义，则查找对应的字段值
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
        
        // 如果简历中没有邮箱字段或为空，则使用用户表中的邮箱
        User user = userService.getUserById(resume.getUserId());
        return user != null ? user.getEmail() : "";
    }
    
    /**
     * 基于约束紧迫度对候选人进行排序，优先满足选择少的候选人
     * 这样可以提高整体分配成功率
     */
    private List<CandidateInfo> sortCandidatesByUrgency(
            List<CandidateInfo> candidates,
            Map<Integer, List<String>> userPreferredTimes,
            Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        // 统计每个时间段的竞争激烈程度
        Map<String, Integer> timeSlotDemand = calculateTimeSlotDemand(userPreferredTimes);
        
        // 计算每个候选人的紧迫度分数
        candidates.forEach(candidate -> {
            double urgencyScore = calculateUrgencyScore(candidate, timeSlotDemand, departmentSlotAvailability);
            candidate.urgencyScore = urgencyScore;
            logger.debug("候选人 {} 的紧迫度分数: {}", 
                    candidate.user.getUsername(), urgencyScore);
        });
        
        // 按紧迫度分数降序排列（分数越高越紧迫，越需要优先分配）
        candidates.sort((c1, c2) -> Double.compare(c2.urgencyScore, c1.urgencyScore));
        
        logger.info("候选人排序完成，前5名紧迫度分数: {}",
                candidates.stream().limit(5).map(c -> String.format("%s:%.2f", 
                        c.user.getUsername(), c.urgencyScore)).collect(Collectors.joining(", ")));
        
        return candidates;
    }
    
    /**
     * 统计每个时间段的需求人数
     */
    private Map<String, Integer> calculateTimeSlotDemand(Map<Integer, List<String>> userPreferredTimes) {
        Map<String, Integer> demand = new HashMap<>();
        
        for (List<String> preferredTimes : userPreferredTimes.values()) {
            for (String timeSlot : preferredTimes) {
                demand.merge(timeSlot, 1, Integer::sum);
            }
        }
        
        logger.info("时间段需求统计: {}", demand);
        return demand;
    }
    
    /**
     * 计算候选人的紧迫度分数
     * 分数越高表示越需要优先分配
     */
    private double calculateUrgencyScore(CandidateInfo candidate, 
                                       Map<String, Integer> timeSlotDemand,
                                       Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        // 基础紧迫度：选择越少越紧迫（1/选择数量）
        double baseUrgency = 1.0 / candidate.preferredTimes.size();
        
        // 稀缺性加权：候选人偏好的时间段竞争越激烈，紧迫度越高
        double scarcityWeight = 0.0;
        for (String timeSlot : candidate.preferredTimes) {
            int demand = timeSlotDemand.getOrDefault(timeSlot, 0);
            int availableSlots = getAvailableSlotsCount(timeSlot, candidate.firstDepartment, departmentSlotAvailability);
            if (availableSlots > 0) {
                // 竞争激烈程度 = 需求人数 / 可用时间槽数
                double competitionRatio = (double) demand / availableSlots;
                scarcityWeight += competitionRatio;
            } else {
                // 如果某个时间段已无可用时间槽，给予最高的稀缺性加权
                scarcityWeight += 10.0;
            }
        }
        scarcityWeight = scarcityWeight / candidate.preferredTimes.size(); // 取平均值
        
        // 综合紧迫度分数：60%基础紧迫度 + 40%稀缺性加权
        return baseUrgency * 0.6 + scarcityWeight * 0.4;
    }
    
    /**
     * 计算指定时间段和部门的可用时间槽数量
     * @param preferredTime 期望的时间段（如"Day 1 上午"）
     * @param department 部门名称
     * @param departmentSlotAvailability 各部门时间槽可用性映射
     * @return 可用时间槽数量
     */
    private int getAvailableSlotsCount(String preferredTime, String department,
                                     Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
        if (slotAvailability == null) {
            return 0;
        }
        
        // 获取所有可用的时间槽
        List<LocalDateTime> availableSlots = slotAvailability.entrySet().stream()
                .filter(Map.Entry::getValue) // 只考虑可用的时间槽
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        // 计算符合期望时间段的时间槽数量
        int count = 0;
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime)) {
                count++;
            }
        }
        
        return count;
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
        
        public String getGroupKey() {
            return department + "|" + timeSlot;
        }
        
        // getter methods
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
        private static final String[] CLASSROOMS = {"教室1", "教室2", "教室3"};
        
        /**
         * 为面试分配教室
         * @param interviewTime 时间段
         * @return 教室编号，如果没有可用教室则返回 null
         */
        public String assignClassroom(LocalDateTime interviewTime) {
            String timeKey = interviewTime.toString();
            int currentCount = timeSlotClassroomCounter.getOrDefault(timeKey, 0);
            
            if (currentCount < CLASSROOMS.length) {
                timeSlotClassroomCounter.put(timeKey, currentCount + 1);
                return CLASSROOMS[currentCount];
            }
            
            return null; // 没有可用教室
        }
        
        /**
         * 检查指定时间是否还有可用教室
         */
        public boolean hasAvailableClassroom(LocalDateTime interviewTime) {
            String timeKey = interviewTime.toString();
            int currentCount = timeSlotClassroomCounter.getOrDefault(timeKey, 0);
            return currentCount < CLASSROOMS.length;
        }
    }
    
    /**
     * 使用三层排序策略对候选人进行排序
     */
    private List<CandidateInfo> sortCandidatesByDepartmentTimeGroup(
            List<CandidateInfo> candidates,
            Map<Integer, List<String>> userPreferredTimes,
            Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        logger.info("开始使用三层排序策略对 {} 个候选人进行排序", candidates.size());
        
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
        
        logger.info("创建了 {} 个部门-时间段组合", groups.size());
        for (DepartmentTimeGroup group : groups.values()) {
            logger.debug("组合 [{}|{}] 包含 {} 个候选人", 
                group.getDepartment(), group.getTimeSlot(), group.getSize());
        }
        
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
            double groupSizeWeight = (double) group.getSize() / maxGroupSize; // 归一化的组大小
            
            // 计算时间稀缺性
            double scarcityScore = calculateTimeSlotScarcity(group.getTimeSlot(), group.getDepartment(), departmentSlotAvailability);
            
            // 计算部门均衡性分数（简化为固定值）
            double departmentBalanceScore = 0.5; // 可以根据实际需要调整
            
            // 组优先级 = 组大小权重 * 0.5 + 时间稀缺性 * 0.3 + 部门均衡性 * 0.2
            double groupPriority = groupSizeWeight * 0.5 + scarcityScore * 0.3 + departmentBalanceScore * 0.2;
            
            group.setGroupPriority(groupPriority);
            
            logger.debug("组合 [{}|{}] 优先级: {:.3f} (组大小:{}, 稀缺性:{:.3f})", 
                group.getDepartment(), group.getTimeSlot(), groupPriority, group.getSize(), scarcityScore);
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
            return 1.0; // 最高稀缺性
        }
        
        // 可以根据实际情况调整这个公式
        return Math.min(1.0, 10.0 / availableSlots);
    }
    
    /**
     * 组内排序：保留原有的个人紧迫度排序逻辑
     */
    private void sortCandidatesWithinGroups(Map<String, DepartmentTimeGroup> groups,
                                           Map<Integer, List<String>> userPreferredTimes,
                                           Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability) {
        
        for (DepartmentTimeGroup group : groups.values()) {
            // 对组内候选人按个人紧迫度排序
            List<CandidateInfo> candidates = group.getCandidates();
            
            // 计算每个候选人的紧迫度分数
            Map<String, Integer> timeSlotDemand = calculateTimeSlotDemand(userPreferredTimes);
            
            candidates.forEach(candidate -> {
                double urgencyScore = calculateUrgencyScore(candidate, timeSlotDemand, departmentSlotAvailability);
                candidate.urgencyScore = urgencyScore;
            });
            
            // 按紧迫度分数降序排列，相同分数的随机打散
            candidates.sort((c1, c2) -> {
                int urgencyCompare = Double.compare(c2.urgencyScore, c1.urgencyScore);
                if (urgencyCompare != 0) {
                    return urgencyCompare;
                }
                // 相同紧迫度的随机打散
                return Integer.compare(c1.user.getUserId().hashCode(), c2.user.getUserId().hashCode());
            });
        }
    }
    
    /**
     * 将分组后的候选人按组优先级重新排列为单一列表
     */
    private List<CandidateInfo> flattenGroupsToSortedList(Map<String, DepartmentTimeGroup> groups) {
        List<CandidateInfo> sortedCandidates = new ArrayList<>();
        
        // 按组优先级降序排列组
        List<DepartmentTimeGroup> sortedGroups = groups.values().stream()
            .sorted((g1, g2) -> Double.compare(g2.getGroupPriority(), g1.getGroupPriority()))
            .collect(Collectors.toList());
        
        logger.info("按组优先级排序后的前5个组合：");
        for (int i = 0; i < Math.min(5, sortedGroups.size()); i++) {
            DepartmentTimeGroup group = sortedGroups.get(i);
            logger.info("  {}: [{}|{}] 优先级:{:.3f} 人数:{}", 
                i + 1, group.getDepartment(), group.getTimeSlot(), 
                group.getGroupPriority(), group.getSize());
        }
        
        // 将各组的候选人按顺序添加到结果列表
        for (DepartmentTimeGroup group : sortedGroups) {
            sortedCandidates.addAll(group.getCandidates());
        }
        
        logger.info("三层排序完成，最终排序结果包含 {} 个候选人", sortedCandidates.size());
        return sortedCandidates;
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
                        logger.debug("解析用户 {} 的期望面试时间字段值: {}", resume.getUserId(), fieldValue);
                        
                        if (fieldValue != null && fieldValue.contains("\"first\"") && fieldValue.contains("\"second\"")) {
                            // 解析包含first和second字段的JSON对象
                            JsonNode jsonNode = objectMapper.readTree(fieldValue);
                            String first = jsonNode.has("first") ? jsonNode.get("first").asText() : null;
                            String second = jsonNode.has("second") ? jsonNode.get("second").asText() : null;
                            
                            logger.debug("用户 {} 的first字段: {}, second字段: {}", resume.getUserId(), first, second);
                            
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
                        
                        logger.debug("用户 {} 添加的期望时间: {}", resume.getUserId(), allPreferredTimes);
                    } catch (Exception e) {
                        logger.warn("解析用户 {} 的期望面试时间失败: {}", resume.getUserId(), 
                                interviewTimeValue.getFieldValue(), e);
                    }
                }
                logger.info("用户 {} 解析后的所有期望面试时间: {}", resume.getUserId(), allPreferredTimes);
                userPreferredTimes.put(resume.getUserId(), allPreferredTimes);
            }
        }
        
        logger.info("总共解析了 {} 个用户的期望面试时间", userPreferredTimes.size());
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
     * 为单个日期生成面试时间槽 (仅9月27日一天，包括上午、下午、晚上)
     */
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
     * 尝试为用户分配面试时间，严格按照用户偏好进行分配
     */
    private boolean tryAssignInterviewTime(User user, Resume resume, List<String> preferredTimes, String department,
                                         Map<String, Map<LocalDateTime, Boolean>> departmentSlotAvailability,
                                         List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignedInterviews,
                                         ClassroomAssigner classroomAssigner) {
        // 如果用户没有填写期望面试时间，则不分配面试时间
        if (preferredTimes == null || preferredTimes.isEmpty()) {
            return false;
        }
        
        logger.info("开始为用户 {} 分配面试时间，期望时间: {}", user.getUsername(), preferredTimes);
        
        // 按照用户期望的时间顺序尝试分配
        for (String preferredTime : preferredTimes) {
            logger.info("尝试为用户 {} 分配时间: {}", user.getUsername(), preferredTime);
            // 检查该时间段是否有空位且有可用教室
            if (hasAvailableSlotAndClassroom(preferredTime, department, departmentSlotAvailability, classroomAssigner)) {
                logger.info("用户 {} 的时间段 {} 有空位且有可用教室，正在分配", user.getUsername(), preferredTime);
                // 如果有空位且有教室，则分配时间
                LocalDateTime assignedSlot = findAndReserveSlot(preferredTime, department, departmentSlotAvailability);
                if (assignedSlot != null) {
                    // 分配教室
                    String classroom = classroomAssigner.assignClassroom(assignedSlot);
                    if (classroom != null) {
                        // 成功分配时间和教室
                        String period;
                        LocalTime timeOfDay = assignedSlot.toLocalTime();
                        if (!timeOfDay.isBefore(MORNING_START) && timeOfDay.isBefore(MORNING_END)) {
                            period = "上午";
                        } else if (!timeOfDay.isBefore(AFTERNOON_START) && timeOfDay.isBefore(AFTERNOON_END)) {
                            period = "下午";
                        } else if (!timeOfDay.isBefore(EVENING_START) && timeOfDay.isBefore(EVENING_END)) {
                            period = "晚上";
                        } else {
                            period = "未知"; // 备用，不应该出现
                        }
                        logger.info("成功为用户 {} 分配面试时间: {} 教室: {}", user.getUsername(), assignedSlot, classroom);
                        // 从简历中获取姓名而不是从用户表中获取
                        String name = getResumeName(resume);
                        // 从简历中获取邮箱而不是从用户表中获取
                        String email = getResumeEmail(resume);
                        assignedInterviews.add(new InterviewAssignmentResultDTO.AssignedInterviewDTO(
                                user.getUserId(), user.getUsername(), name, email, assignedSlot, period, department, classroom));
                        return true;
                    } else {
                        // 没有可用教室，需要释放时间槽
                        Map<LocalDateTime, Boolean> slotAvailability = departmentSlotAvailability.get(department);
                        if (slotAvailability != null) {
                            slotAvailability.put(assignedSlot, true); // 释放时间槽
                        }
                        logger.warn("用户 {} 的时间段 {} 没有可用教室，释放时间槽", user.getUsername(), preferredTime);
                    }
                }
            } else {
                logger.info("用户 {} 的时间段 {} 没有空位或没有可用教室，尝试下一个时间段", user.getUsername(), preferredTime);
            }
        }
        
        // 如果用户选择的所有时间段都没有空位或没有可用教室，则不分配时间
        logger.info("用户 {} 的所有期望时间段都没有空位或没有可用教室，无法分配", user.getUsername());
        return false;
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
                .filter(Map.Entry::getValue) // 只考虑可用的时间槽
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        
        logger.debug("部门 {} 的可用时间槽: {}", department, availableSlots);
        
        // 检查是否有符合期望时间段的时间槽且有可用教室
        for (LocalDateTime slotTime : availableSlots) {
            if (isSlotMatchPreference(slotTime, preferredTime) && classroomAssigner.hasAvailableClassroom(slotTime)) {
                logger.debug("找到匹配的时间槽: {} 对应偏好时间: {} 且有可用教室", slotTime, preferredTime);
                return true;
            }
        }
        
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
     * 检查时间槽是否符合用户的期望时间段
     * 严格按照用户选择的具体日期和时间段进行匹配
     */
    private boolean isSlotMatchPreference(LocalDateTime slotTime, String preferredTime) {
        // 处理格式如 "Day 1 上午"、"Day 1 下午"、"Day 1 晚上"
        String[] parts = preferredTime.split(" ");
        if (parts.length < 3 || !"Day".equals(parts[0])) {
            logger.warn("无效的期望时间格式: {}", preferredTime);
            return false;
        }
        
        try {
            int dayNumber = Integer.parseInt(parts[1]);
            String period = parts[2]; // "上午"、"下午"、"晚上"
            
            // 现在只有Day 1，对应9月27日
            LocalDate day1 = LocalDate.of(2025, 9, 27);
            
            // 只匹配Day 1，即9月27日
            if (dayNumber == 1 && slotTime.toLocalDate().equals(day1)) {
                // 检查时间段是否匹配
                LocalTime time = slotTime.toLocalTime();
                if ("上午".equals(period)) {
                    return !time.isBefore(MORNING_START) && time.isBefore(MORNING_END);
                } else if ("下午".equals(period)) {
                    return !time.isBefore(AFTERNOON_START) && time.isBefore(AFTERNOON_END);
                } else if ("晚上".equals(period)) {
                    return !time.isBefore(EVENING_START) && time.isBefore(EVENING_END);
                }
            } else {
                logger.debug("时间槽 {} 与期望时间 {} 不匹配: 日期不匹配", slotTime, preferredTime);
            }
        } catch (NumberFormatException e) {
            logger.warn("解析期望时间失败: {}", preferredTime, e);
            return false;
        }
        
        return false;
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
        private double urgencyScore; // 紧迫度分数
        
        public CandidateInfo(User user, List<String> preferredTimes, List<String> preferredDepartments, String firstDepartment, Resume resume) {
            this.user = user;
            this.preferredTimes = preferredTimes;
            this.preferredDepartments = preferredDepartments;
            this.firstDepartment = firstDepartment;
            this.resume = resume;
            this.urgencyScore = 0.0;
        }
    }
}