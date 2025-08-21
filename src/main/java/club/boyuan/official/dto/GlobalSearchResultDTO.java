package club.boyuan.official.dto;

import club.boyuan.official.entity.User;
import club.boyuan.official.entity.AwardExperience;

import java.time.LocalDate;
import java.util.List;

/**
 * 全局搜索结果DTO
 */
public class GlobalSearchResultDTO {
    private List<UserSearchResult> users;
    private List<AwardSearchResult> awards;

    // Getters and Setters
    public List<UserSearchResult> getUsers() {
        return users;
    }

    public void setUsers(List<UserSearchResult> users) {
        this.users = users;
    }

    public List<AwardSearchResult> getAwards() {
        return awards;
    }

    public void setAwards(List<AwardSearchResult> awards) {
        this.awards = awards;
    }

    /**
     * 用户搜索结果
     */
    public static class UserSearchResult {
        private Integer userId;
        private String username;
        private String name;
        private String email;
        private String phone;
        private String dept;

        public UserSearchResult() {}

        public UserSearchResult(User user) {
            this.userId = user.getUserId();
            this.username = user.getUsername();
            this.name = user.getName();
            this.email = user.getEmail();
            this.phone = user.getPhone();
            this.dept = user.getDept();
        }

        // Getters and Setters
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

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getDept() {
            return dept;
        }

        public void setDept(String dept) {
            this.dept = dept;
        }
    }

    /**
     * 奖项搜索结果
     */
    public static class AwardSearchResult {
        private Integer awardId;
        private Integer userId;
        private String awardName;
        private LocalDate awardTime;
        private String description;

        public AwardSearchResult() {}

        public AwardSearchResult(AwardExperience award) {
            this.awardId = award.getAwardId();
            this.userId = award.getUserId();
            this.awardName = award.getAwardName();
            this.awardTime = award.getAwardTime();
            this.description = award.getDescription();
        }

        // Getters and Setters
        public Integer getAwardId() {
            return awardId;
        }

        public void setAwardId(Integer awardId) {
            this.awardId = awardId;
        }

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getAwardName() {
            return awardName;
        }

        public void setAwardName(String awardName) {
            this.awardName = awardName;
        }

        public LocalDate getAwardTime() {
            return awardTime;
        }

        public void setAwardTime(LocalDate awardTime) {
            this.awardTime = awardTime;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}