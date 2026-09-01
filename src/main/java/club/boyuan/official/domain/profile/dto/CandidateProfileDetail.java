package club.boyuan.official.domain.profile.dto;

import club.boyuan.official.persistence.entity.AwardExperience;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.Resume;
import lombok.Data;

import java.util.List;

/** 候选档案详情：某用户全部周期的聚合视图。 */
@Data
public class CandidateProfileDetail {
    private Integer userId;
    private String name;
    private String username;
    private String major;
    private String email;
    private String phone;
    private String github;
    private String deptName;

    /** 全部周期的面试安排（按时间升序） */
    private List<InterviewScheduleSection> interviews;
    /** 全部周期的评测提交（按时间倒序） */
    private List<EvaluationSubmission> submissions;
    /** 全部获奖记录 */
    private List<AwardExperience> awards;
    /** 全部简历摘要 */
    private List<Resume> resumes;

    /** 一条面试安排 + 其地点/周期名（展开简历与场次信息） */
    @Data
    public static class InterviewScheduleSection {
        private InterviewSchedule schedule;
        private String location;
        private String cycleName;
        private String deptName;
    }
}