package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.EvaluationDimensionDTO;
import club.boyuan.official.domain.interview.dto.SaveEvaluationDimensionsRequestDTO;

import java.util.List;

/**
 * 面试评分维度模板：决定协同评价表有哪些评分列。
 *
 * @author dhy
 */
public interface IEvaluationDimensionService {

    /**
     * 列出某周期的评分维度（按 sortOrder 升序）。
     */
    List<EvaluationDimensionDTO> listByCycle(Integer cycleId);

    /**
     * 整表覆盖某周期的评分维度：请求中未出现的既有维度会被删除。
     * <p>
     * 删除维度不会清理已物化的评分——历史评价里该维度的得分仍保留在 scores JSON 中，
     * 只是不再作为列展示，也不再计入新的加权总分。
     */
    List<EvaluationDimensionDTO> replaceDimensions(Integer cycleId, SaveEvaluationDimensionsRequestDTO request);
}
