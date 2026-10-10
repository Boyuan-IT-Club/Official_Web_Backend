package club.boyuan.official.domain.interview.service.impl;

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

    private final InterviewScheduleMapper interviewScheduleMapper;
    private final SessionInterviewerMapper sessionInterviewerMapper;

    @Override
    public boolean canSeeCandidateMaterials(Integer viewerUserId, Integer candidateUserId) {
        if (viewerUserId == null || candidateUserId == null) {
            return false;
        }

        // 候选人被排进的全部场次（跨周期；历史周期的面试官同样有理由回看自己面过的人）
        List<Integer> sessionIds = interviewScheduleMapper.selectList(
                        new LambdaQueryWrapper<InterviewSchedule>()
                                .eq(InterviewSchedule::getUserId, candidateUserId))
                .stream()
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            // 还没排期，或已改为线上面试（不占场次）——此时没有任何绑定关系可依，一律拒绝
            return false;
        }

        return sessionInterviewerMapper.exists(new LambdaQueryWrapper<SessionInterviewer>()
                .eq(SessionInterviewer::getUserId, viewerUserId)
                .in(SessionInterviewer::getSessionId, sessionIds));
    }
}
