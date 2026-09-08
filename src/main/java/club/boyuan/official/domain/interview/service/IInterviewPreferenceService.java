package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.InterviewPreferenceDTO;
import club.boyuan.official.domain.interview.dto.SubmitInterviewPreferenceRequestDTO;
import club.boyuan.official.persistence.entity.InterviewPreference;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 学生面试志愿服务
 */
public interface IInterviewPreferenceService extends IService<InterviewPreference> {

    /**
     * 提交/更新当前学生在指定周期的面试志愿（至多两个志愿部门 + 多个可接受时间窗）。
     */
    InterviewPreferenceDTO submitPreference(Integer userId, SubmitInterviewPreferenceRequestDTO request);

    /**
     * 查询当前学生在指定周期的志愿；未填写时返回 null。
     */
    InterviewPreferenceDTO getMyPreference(Integer userId, Integer cycleId);

    /**
     * 更新「能否到线下参加面试」（写回简历字段 expected_interview_time 的 JSON）。
     * 已排上生效场次的学生要走改期申请，这里会拒绝。
     */
    java.util.Map<String, Object> updateAttendance(Integer userId,
            club.boyuan.official.domain.interview.dto.UpdateAttendanceRequestDTO request);
}
