package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import jakarta.persistence.*;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "award_experience")
@TableName("award_experience") // MyBatis-Plus 表映射注解
public class AwardExperience {
    @Id
    @TableId(value = "award_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "award_id")
    private Integer awardId;

    @Column(name = "user_id", nullable = false)
    @TableField("user_id") // MyBatis-Plus 字段映射
    private Integer userId;

    @Column(name = "award_name", nullable = false)
    @TableField("award_name")
    private String awardName;

    @Column(name = "award_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("award_time")
    private LocalDate awardTime;

    @Column(name = "description", columnDefinition = "TEXT")
    @TableField("description")
    private String description;

    // 构造函数
    public AwardExperience() {
    }

    public AwardExperience(Integer userId, String awardName, LocalDate awardTime, String description) {
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