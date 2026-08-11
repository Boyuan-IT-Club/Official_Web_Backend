package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.evaluation.dto.Report;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 报告单明文 JSON → Report DTO,并做最小 schema 校验。
 */
public final class ReportParser {

    private ReportParser() {
    }

    public static Report parse(String plainJson) {
        Report report;
        try {
            report = new ObjectMapper().readValue(plainJson, Report.class);
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "报告单明文解析失败: " + e.getMessage());
        }
        if (report == null || report.getAuthor() == null || report.getTimestamp() == null
                || report.getTasks() == null || report.getTotalScore() == null) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "报告单缺少必需字段");
        }
        return report;
    }
}