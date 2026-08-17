package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.evaluation.dto.TrendPoint;
import club.boyuan.official.persistence.entity.EvaluationSubmission;

import java.util.List;

/**
 * 用户端评测查询(本人视角)。
 */
public interface IEvaluationUserService {

    PageResultDTO<EvaluationSubmission> page(Integer userId, int page, int size);

    EvaluationSubmission latest(Integer userId);

    List<TrendPoint> trend(Integer userId);
}