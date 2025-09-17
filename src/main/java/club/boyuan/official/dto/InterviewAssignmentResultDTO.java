package club.boyuan.official.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试时间分配结果DTO
 */
public class InterviewAssignmentResultDTO {
    
    /**
     * 成功分配的面试列表
     */
    private List<AssignedInterviewDTO> assignedInterviews;
    
    /**
     * 未能分配的用户列表
     */
    private List<UnassignedUserDTO> unassignedUsers;
    
    /**
     * 未填写期望面试时间的用户列表
     */
    private List<NoPreferenceUserDTO> noPreferenceUsers;
    
    public InterviewAssignmentResultDTO() {
    }
    
    public InterviewAssignmentResultDTO(List<AssignedInterviewDTO> assignedInterviews, List<UnassignedUserDTO> unassignedUsers) {
        this.assignedInterviews = assignedInterviews;
        this.unassignedUsers = unassignedUsers;
    }
    
    public InterviewAssignmentResultDTO(List<AssignedInterviewDTO> assignedInterviews, 
                                      List<UnassignedUserDTO> unassignedUsers,
                                      List<NoPreferenceUserDTO> noPreferenceUsers) {
        this.assignedInterviews = assignedInterviews;
        this.unassignedUsers = unassignedUsers;
        this.noPreferenceUsers = noPreferenceUsers;
    }
    
    // Getter和Setter方法
    
    public List<AssignedInterviewDTO> getAssignedInterviews() {
        return assignedInterviews;
    }
    
    public void setAssignedInterviews(List<AssignedInterviewDTO> assignedInterviews) {
        this.assignedInterviews = assignedInterviews;
    }
    
    public List<UnassignedUserDTO> getUnassignedUsers() {
        return unassignedUsers;
    }
    
    public void setUnassignedUsers(List<UnassignedUserDTO> unassignedUsers) {
        this.unassignedUsers = unassignedUsers;
    }
    
    public List<NoPreferenceUserDTO> getNoPreferenceUsers() {
        return noPreferenceUsers;
    }
    
    public void setNoPreferenceUsers(List<NoPreferenceUserDTO> noPreferenceUsers) {
        this.noPreferenceUsers = noPreferenceUsers;
    }
    
    /**
     * 已分配的面试信息
     */
    public static class AssignedInterviewDTO {
        private Integer userId;
        private String username;
        private String name;
        private String email; // 添加邮箱字段
        private LocalDateTime interviewTime;
        private String period; // 上午/下午
        private String interviewDepartment; // 面试部门（第一志愿）
        private String classroom; // 教室信息
        
        public AssignedInterviewDTO() {
        }
        
        public AssignedInterviewDTO(Integer userId, String username, String name, String email, LocalDateTime interviewTime, String period, String interviewDepartment, String classroom) {
            this.userId = userId;
            this.username = username;
            this.name = name;
            this.email = email;
            this.interviewTime = interviewTime;
            this.period = period;
            this.interviewDepartment = interviewDepartment;
            this.classroom = classroom;
        }
        
        // Getter和Setter方法
        
        public Integer getUserId() {
            return userId;
        }
        
        public void setUserId(Integer userId) {
            this.userId = userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public LocalDateTime getInterviewTime() {
            return interviewTime;
        }
        
        public void setInterviewTime(LocalDateTime interviewTime) {
            this.interviewTime = interviewTime;
        }
        
        public String getPeriod() {
            return period;
        }
        
        public void setPeriod(String period) {
            this.period = period;
        }
        
        public String getInterviewDepartment() {
            return interviewDepartment;
        }
        
        public void setInterviewDepartment(String interviewDepartment) {
            this.interviewDepartment = interviewDepartment;
        }
        
        public String getClassroom() {
            return classroom;
        }
        
        public void setClassroom(String classroom) {
            this.classroom = classroom;
        }
    }
    
    /**
     * 未分配的用户信息
     */
    public static class UnassignedUserDTO {
        private Integer userId;
        private String username;
        private String name;
        private String email; // 添加邮箱字段
        private String preferredTimes; // 用户期望的面试时间
        private String preferredDepartments; // 用户期望的面试部门
        
        public UnassignedUserDTO() {
        }
        
        public UnassignedUserDTO(Integer userId, String username, String name, String email, String preferredTimes, String preferredDepartments) {
            this.userId = userId;
            this.username = username;
            this.name = name;
            this.email = email;
            this.preferredTimes = preferredTimes;
            this.preferredDepartments = preferredDepartments;
        }
        
        // Getter和Setter方法
        
        public Integer getUserId() {
            return userId;
        }
        
        public void setUserId(Integer userId) {
            this.userId = userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getPreferredTimes() {
            return preferredTimes;
        }
        
        public void setPreferredTimes(String preferredTimes) {
            this.preferredTimes = preferredTimes;
        }
        
        public String getPreferredDepartments() {
            return preferredDepartments;
        }
        
        public void setPreferredDepartments(String preferredDepartments) {
            this.preferredDepartments = preferredDepartments;
        }
    }
    
    /**
     * 未填写期望面试时间的用户信息
     */
    public static class NoPreferenceUserDTO {
        private Integer userId;
        private String username;
        private String name;
        private String email; // 添加邮箱字段
        
        public NoPreferenceUserDTO() {
        }
        
        public NoPreferenceUserDTO(Integer userId, String username, String name, String email) {
            this.userId = userId;
            this.username = username;
            this.name = name;
            this.email = email;
        }
        
        // Getter和Setter方法
        
        public Integer getUserId() {
            return userId;
        }
        
        public void setUserId(Integer userId) {
            this.userId = userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
    }
}