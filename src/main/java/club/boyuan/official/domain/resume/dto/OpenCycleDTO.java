package club.boyuan.official.domain.resume.dto;

import club.boyuan.official.persistence.entity.RecruitmentCycle;

import java.time.LocalDate;

/**
 * 一个「当前开放投递」的招募周期，给用户端的周期选择器用。
 *
 * 额外带 fieldCount：该周期配置了多少个简历字段。
 * 之所以要这个数：/api/resumes/fields/{cycleId} 在没有任何字段定义时返回
 * 200 + 空数组（不报错），用户端照着渲染就是一张零字段的空表单 ——
 * 表现为「页面显示不出来」，而且前后端都不会有任何报错。有了这个数，
 * 选择器可以直接标出「该周期未配置报名表单」。
 */
public class OpenCycleDTO {

    private Integer cycleId;
    private String cycleName;
    private String academicYear;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 该周期已配置的简历字段数；为 0 表示表单还没配，投递页无内容可填 */
    private int fieldCount;
    /**
     * 是否仍接收投递（is_active = 1）。
     * false = 管理员已点「停止投递」：周期在时间内仍对用户端可见（看简历、看进度），
     * 但不能提交、修改或新建简历——后端 requireCycleOpen 同样会拒绝。
     */
    private boolean intakeOpen;
    /**
     * 本届负责人联系方式。投递阶段学生要找人问（材料要求、时间冲突、
     * 线上面试怎么申请），此前这个值只有未录取邮件在用，用户端拿不到，
     * 导致填写提示里的联系方式一直是空的。
     */
    private String contactInfo;

    public OpenCycleDTO() {
    }

    public OpenCycleDTO(RecruitmentCycle cycle, int fieldCount) {
        this.cycleId = cycle.getCycleId();
        this.cycleName = cycle.getCycleName();
        this.academicYear = cycle.getAcademicYear();
        this.description = cycle.getDescription();
        this.startDate = cycle.getStartDate();
        this.endDate = cycle.getEndDate();
        this.fieldCount = fieldCount;
        this.intakeOpen = Integer.valueOf(1).equals(cycle.getIsActive());
        this.contactInfo = cycle.getContactInfo();
    }

    public Integer getCycleId() {
        return cycleId;
    }

    public void setCycleId(Integer cycleId) {
        this.cycleId = cycleId;
    }

    public String getCycleName() {
        return cycleName;
    }

    public void setCycleName(String cycleName) {
        this.cycleName = cycleName;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public boolean isIntakeOpen() {
        return intakeOpen;
    }

    public void setIntakeOpen(boolean intakeOpen) {
        this.intakeOpen = intakeOpen;
    }
}
