package club.boyuan.official.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "award_experience")
public class AwardExperience {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "award_id")
    private Integer awardId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "award_name", nullable = false)
    private String awardName;

    @Column(name = "award_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime awardTime;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 构造函数
    public AwardExperience() {
    }

    public AwardExperience(Integer userId, String awardName, LocalDateTime awardTime, String description) {
        this.userId = userId;
        this.awardName = awardName;
        this.awardTime = awardTime;
        this.description = description;
    }

    // Getter 和 Setter 方法
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

    public LocalDateTime getAwardTime() {
        return awardTime;
    }

    public void setAwardTime(LocalDateTime awardTime) {
        this.awardTime = awardTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}