package club.boyuan.official.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一键分配面试响应DTO
 */
@Data
public class AutoAssignInterviewResponseDTO {
    
    /**
     * 成功分配的数量
     */
    private Integer assignedCount;
    
    /**
     * 未分配的用户数量
     */
    private Integer unassignedCount;
    
    /**
     * 未填写偏好的用户数量
     */
    private Integer noPreferenceCount;
    
    /**
     * 分配详情列表
     */
    private List<AssignmentDetailDTO> assignmentDetails;
    
    /**
     * 未分配用户列表
     */
    private List<UnassignedUserDTO> unassignedUsers;
    
    /**
     * 未填写偏好的用户列表
     */
    private List<NoPreferenceUserDTO> noPreferenceUsers;
    
    /**
     * 分配时间
     */
    private LocalDateTime assignmentTime;
    
    /**
     * 分配详情DTO
     */
    @Data
    public static class AssignmentDetailDTO {
        private Integer userId;
        private String username;
        private String name;
        private String email;
        private Integer slotId;
        private LocalDateTime interviewTime;
        private String location;
        private String period;
        private String department;
        private String classroom;
        private String preferredDepartments;
        private String preferredTimes;
    }
    
    /**
     * 未分配用户DTO
     */
    @Data
    public static class UnassignedUserDTO {
        private Integer userId;
        private String username;
        private String name;
        private String email;
        private String major;
        private String grade;
        private String preferredTimes;
        private String preferredDepartments;
        
        public UnassignedUserDTO() {}
        
        public UnassignedUserDTO(Integer userId, String username, String name, String email, 
                               String major, String grade, String preferredTimes, String preferredDepartments) {
            this.userId = userId;
            this.username = username;
            this.name = name;
            this.email = email;
            this.major = major;
            this.grade = grade;
            this.preferredTimes = preferredTimes;
            this.preferredDepartments = preferredDepartments;
        }
    }
    
    /**
     * 未填写偏好用户DTO
     */
    @Data
    public static class NoPreferenceUserDTO {
        private Integer userId;
        private String username;
        private String name;
        private String email;
        private String major;
        private String grade;
        
        public NoPreferenceUserDTO() {}
        
        public NoPreferenceUserDTO(Integer userId, String username, String name, String email, 
                                 String major, String grade) {
            this.userId = userId;
            this.username = username;
            this.name = name;
            this.email = email;
            this.major = major;
            this.grade = grade;
        }
    }
}