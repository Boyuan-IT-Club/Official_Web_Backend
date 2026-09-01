package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.EvaluationSummaryDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationRequestDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationResultDTO;

/**
 * 面试评价的物化与汇总：CRDT 文档与业务库之间的那条链路。
 *
 * @author dhy
 */
public interface IInterviewEvaluationService {

    /**
     * 协同服务把解析后的评价数据批量回写业务库。
     * <p>
     * 越权条目（写入者与该单元格归属的面试官不一致）会被逐条丢弃并记入审计，
     * 不影响同一批次中的合法条目。
     */
    MaterializeEvaluationResultDTO materialize(MaterializeEvaluationRequestDTO request);

    /**
     * 全周期评价汇总：候选人 × 面试官矩阵，读物化后的 interview_evaluation。
     */
    EvaluationSummaryDTO summary(Integer cycleId);
}
