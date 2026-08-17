package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.evaluation.dto.CandidateRow;
import club.boyuan.official.persistence.entity.EvaluationSubmission;

import java.util.List;

/**
 * 管理端评测查询与认领(需 evaluation:view 权限)。
 */
public interface IEvaluationAdminService {

    PageResultDTO<CandidateRow> candidates(Integer cycleId, Integer deptId,
                                           Integer minScore, Integer maxScore,
                                           String claimed, String sortBy, int page, int size);

    List<EvaluationSubmission> submissions(String key);

    EvaluationSubmission detail(Long id);

    EvaluationSubmission claim(Long id, Integer userId);
}