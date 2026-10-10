package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.domain.resume.service.CandidateMaterialScope;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 按场次绑定判定面试官能看哪些候选人的材料。
 *
 * <p>与 {@code EvaluationBoardServiceImpl#getCandidateResume} 同一条规则：
 * 面试官看得到的，恰好是自己负责的场次里的那几个人。
 *
 * @author dhy
 */
@Service
@RequiredArgsConstructor
public class InterviewerMaterialScope implements CandidateMaterialScope {

    /** 面试形式：线上（不占场次） */
    private static final int INTERVIEW_MODE_ONLINE = 1;

    private final InterviewScheduleMapper interviewScheduleMapper;
    private final SessionInterviewerMapper sessionInterviewerMapper;
    private final IEvaluationBoardService evaluationBoardService;

    @Override
    public boolean canSeeCandidateMaterials(Integer viewerUserId, Integer candidateUserId) {
        if (viewerUserId == null || candidateUserId == null) {
            return false;
        }

        // 候选人被排进的全部安排（跨周期；历史周期的面试官同样有理由回看自己面过的人）
        List<InterviewSchedule> schedules = interviewScheduleMapper.selectList(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getUserId, candidateUserId));
        if (schedules.isEmpty()) {
            return false;
        }

        // 线下：按场次绑定
        List<Integer> sessionIds = schedules.stream()
                .filter(s -> !Objects.equals(s.getInterviewMode(), INTERVIEW_MODE_ONLINE))
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!sessionIds.isEmpty() && sessionInterviewerMapper.exists(
                new LambdaQueryWrapper<SessionInterviewer>()
                        .eq(SessionInterviewer::getUserId, viewerUserId)
                        .in(SessionInterviewer::getSessionId, sessionIds))) {
            return true;
        }

        // 线上面试不占场次，没有绑定关系可依 —— 退到「本周期的面试官都算」，
        // 与评价表里线上面试那一组的可编辑范围保持同一条规则。
        return schedules.stream()
                .filter(s -> Objects.equals(s.getInterviewMode(), INTERVIEW_MODE_ONLINE))
                .map(InterviewSchedule::getCycleId)
                .filter(Objects::nonNull)
                .distinct()
                .anyMatch(cycleId -> evaluationBoardService.isInterviewerOfCycle(cycleId, viewerUserId));
    }
}
