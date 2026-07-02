package club.boyuan.official.service;

import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.entity.ResumeFieldValue;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 简历提交前校验：必填 EAV 字段、草稿状态等。
 */
@Component
@RequiredArgsConstructor
public class ResumeSubmitValidator {

    private final IResumeFieldDefinitionService fieldDefinitionService;

    public void validateRequiredFields(Integer resumeId, Integer cycleId, List<ResumeFieldValue> fieldValues) {
        List<ResumeFieldDefinition> definitions = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        Map<Integer, String> valueByFieldId = fieldValues.stream()
                .filter(v -> v.getFieldId() != null)
                .collect(Collectors.toMap(
                        ResumeFieldValue::getFieldId,
                        v -> v.getFieldValue() == null ? "" : v.getFieldValue(),
                        (a, b) -> b));

        List<String> missingLabels = definitions.stream()
                .filter(def -> Boolean.TRUE.equals(def.getIsActive()))
                .filter(def -> Boolean.TRUE.equals(def.getIsRequired()))
                .filter(def -> !StringUtils.hasText(valueByFieldId.get(def.getFieldId())))
                .map(ResumeFieldDefinition::getFieldLabel)
                .toList();

        if (!missingLabels.isEmpty()) {
            throw new BusinessException(
                    BusinessExceptionEnum.RESUME_SUBMIT_VALIDATION_FAILED,
                    "以下必填项未填写: " + String.join("、", missingLabels));
        }
    }

    public void assertDraftEditable(Integer status) {
        if (status != null && status >= 2) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
        }
    }
}
