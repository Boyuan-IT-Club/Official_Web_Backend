package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@TableName("evaluation_submission")
public class EvaluationSubmission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("github_username")
    private String githubUsername;

    @TableField("user_id")
    private Integer userId;

    @TableField("cycle_id")
    private Integer cycleId;

    @TableField("report_sha")
    private String reportSha;

    @TableField("author")
    private String author;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("evaluated_at")
    private LocalDateTime evaluatedAt;

    @TableField("total_score")
    private Integer totalScore;

    @TableField("max_score")
    private Integer maxScore;

    @TableField("task1_score")
    private Integer task1Score;

    @TableField("task2_score")
    private Integer task2Score;

    @TableField("task3_score")
    private Integer task3Score;

    @TableField("task4_score")
    private Integer task4Score;

    @TableField("report_json")
    private String reportJson;

    @TableField("repository")
    private String repository;

    @TableField("commit_sha")
    private String commitSha;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    public EvaluationSubmission() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCycleId() {
        return cycleId;
    }

    public void setCycleId(Integer cycleId) {
        this.cycleId = cycleId;
    }

    public String getReportSha() {
        return reportSha;
    }

    public void setReportSha(String reportSha) {
        this.reportSha = reportSha;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public Integer getTask1Score() {
        return task1Score;
    }

    public void setTask1Score(Integer task1Score) {
        this.task1Score = task1Score;
    }

    public Integer getTask2Score() {
        return task2Score;
    }

    public void setTask2Score(Integer task2Score) {
        this.task2Score = task2Score;
    }

    public Integer getTask3Score() {
        return task3Score;
    }

    public void setTask3Score(Integer task3Score) {
        this.task3Score = task3Score;
    }

    public Integer getTask4Score() {
        return task4Score;
    }

    public void setTask4Score(Integer task4Score) {
        this.task4Score = task4Score;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}