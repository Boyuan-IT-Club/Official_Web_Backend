package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.RecruitFlowProgressDTO;
import club.boyuan.official.domain.resume.service.impl.ResumeServiceImpl;
import club.boyuan.official.persistence.entity.*;
import club.boyuan.official.persistence.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统计一届招新每个环节的产出量，供管理端流程指引使用。
 *
 * 全部是 count 查询，走各表已有的 cycle_id 索引；管理端切周期时调一次，
 * 没有必要为它单开缓存。
 */
@Service
@RequiredArgsConstructor
public class RecruitFlowProgressService {

    private final ResumeFieldDefinitionMapper fieldDefinitionMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewScheduleMapper scheduleMapper;
    private final InterviewEvaluationMapper evaluationMapper;
    private final InterviewResultMapper resultMapper;
    private final PreAdmissionDraftMapper preAdmissionDraftMapper;

    public RecruitFlowProgressDTO of(Integer cycleId) {
        return RecruitFlowProgressDTO.builder()
                .fieldCount(fieldDefinitionMapper.selectCount(new LambdaQueryWrapper<ResumeFieldDefinition>()
                        .eq(ResumeFieldDefinition::getCycleId, cycleId)
                        .eq(ResumeFieldDefinition::getIsActive, true)))
                // 草稿（status=1）不算提交；已初筛的 4/5 仍是提交过的人，所以取 >=2
                .submittedResumes(resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getCycleId, cycleId)
                        .ge(Resume::getStatus, ResumeServiceImpl.STATUS_SUBMITTED)))
                .screenedResumes(resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getCycleId, cycleId)
                        .in(Resume::getStatus,
                                ResumeServiceImpl.STATUS_SCREEN_PASSED,
                                ResumeServiceImpl.STATUS_SCREEN_REJECTED)))
                .schedules(scheduleMapper.selectCount(new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .eq(InterviewSchedule::getStatus, 1)))
                // 草稿评价不算「写完了」，只数已定稿的
                .finalizedEvaluations(evaluationMapper.selectCount(new LambdaQueryWrapper<InterviewEvaluation>()
                        .eq(InterviewEvaluation::getCycleId, cycleId)
                        .eq(InterviewEvaluation::getStatus, InterviewEvaluation.STATUS_SUBMITTED)))
                .preAdmitted(preAdmissionDraftMapper.selectCount(new LambdaQueryWrapper<PreAdmissionDraft>()
                        .eq(PreAdmissionDraft::getCycleId, cycleId)))
                // decision=0 是「待定」，生成名单时的初始值，不算已决定
                .decided(resultMapper.selectCount(new LambdaQueryWrapper<InterviewResult>()
                        .eq(InterviewResult::getCycleId, cycleId)
                        .ne(InterviewResult::getDecision, 0)))
                .notified(resultMapper.selectCount(new LambdaQueryWrapper<InterviewResult>()
                        .eq(InterviewResult::getCycleId, cycleId)
                        .isNotNull(InterviewResult::getNotifiedAt)))
                .build();
    }
}
