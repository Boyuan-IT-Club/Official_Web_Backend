package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.domain.resume.service.CandidateMaterialScope;
import club.boyuan.official.persistence.mapper.EvaluationScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewerMaterialScope implements CandidateMaterialScope {
    private final EvaluationScopeMapper scope;

    @Override
    public java.util.List<String> currentGrants(Integer viewer) {
        return viewer == null ? java.util.List.of() : scope.materialGrants(viewer);
    }

    @Override
    public boolean canReadResume(Integer viewerUserId, Integer resumeId) {
        return viewerUserId != null && resumeId != null
                && scope.candidate(viewerUserId, resumeId, null, null, null, false);
    }
}
