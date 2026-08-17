package club.boyuan.official.domain.evaluation;

import club.boyuan.official.domain.evaluation.dto.EvaluationIntakeRequest;
import club.boyuan.official.persistence.entity.EvaluationSubmission;

/**
 * 评测提交入库:解密 → 校验 → 幂等落库。
 */
public interface IEvaluationIntakeService {

    /**
     * 处理一条 Actions 推送。按报告 sha256 幂等:重复推送返回已存记录。
     *
     * @return 入库(或已存在)的提交记录
     */
    EvaluationSubmission ingest(EvaluationIntakeRequest request);
}