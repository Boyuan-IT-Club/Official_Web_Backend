package club.boyuan.official.feishu;

import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.entity.ResumeFieldValue;
import club.boyuan.official.mapper.ResumeFieldDefinitionMapper;
import club.boyuan.official.service.IResumeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 从 EAV 简历字段中读取常用展示字段。
 */
@Component
@RequiredArgsConstructor
public class ResumeFieldReader {

    private static final List<String> FIELD_KEYS = List.of(
            "name", "grade", "major", "self_introduction", "expected_departments");

    private final IResumeService resumeService;
    private final ResumeFieldDefinitionMapper fieldDefinitionMapper;
    private final ObjectMapper objectMapper;

    public ResumeSnapshot readSnapshot(Resume resume, Integer cycleId) {
        if (resume == null) {
            return ResumeSnapshot.empty();
        }
        Map<String, Integer> keyToFieldId = loadFieldIdMap(cycleId);
        List<ResumeFieldValue> values = resumeService.getFieldValuesByResumeId(resume.getResumeId());
        Map<Integer, String> valueByFieldId = values.stream()
                .filter(v -> v.getFieldId() != null)
                .collect(Collectors.toMap(ResumeFieldValue::getFieldId, ResumeFieldValue::getFieldValue, (a, b) -> a));

        String firstDept = "";
        String secondDept = "";
        String deptRaw = getByKey(keyToFieldId, valueByFieldId, "expected_departments");
        if (StringUtils.hasText(deptRaw)) {
            List<String> depts = parseDepartments(deptRaw);
            if (!depts.isEmpty()) {
                firstDept = depts.get(0);
            }
            if (depts.size() > 1) {
                secondDept = depts.get(1);
            }
        }

        return new ResumeSnapshot(
                nullToEmpty(getByKey(keyToFieldId, valueByFieldId, "name")),
                firstDept,
                secondDept,
                formatIntendedDepartments(firstDept, secondDept),
                nullToEmpty(getByKey(keyToFieldId, valueByFieldId, "grade")),
                nullToEmpty(getByKey(keyToFieldId, valueByFieldId, "major")),
                nullToEmpty(getByKey(keyToFieldId, valueByFieldId, "self_introduction")),
                resume.getResumeScore() == null ? 0 : resume.getResumeScore()
        );
    }

    private String formatIntendedDepartments(String first, String second) {
        if (!StringUtils.hasText(first) && !StringUtils.hasText(second)) {
            return "";
        }
        if (!StringUtils.hasText(second)) {
            return first.trim();
        }
        if (!StringUtils.hasText(first)) {
            return second.trim();
        }
        return first.trim() + " / " + second.trim();
    }

    private Map<String, Integer> loadFieldIdMap(Integer cycleId) {
        LambdaQueryWrapper<ResumeFieldDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResumeFieldDefinition::getCycleId, cycleId)
                .in(ResumeFieldDefinition::getFieldKey, FIELD_KEYS)
                .eq(ResumeFieldDefinition::getIsActive, true);
        List<ResumeFieldDefinition> defs = fieldDefinitionMapper.selectList(wrapper);
        Map<String, Integer> map = new HashMap<>();
        for (ResumeFieldDefinition def : defs) {
            map.put(def.getFieldKey(), def.getFieldId());
        }
        return map;
    }

    private String getByKey(Map<String, Integer> keyToFieldId, Map<Integer, String> valueByFieldId, String key) {
        Integer fieldId = keyToFieldId.get(key);
        if (fieldId == null) {
            return "";
        }
        return valueByFieldId.getOrDefault(fieldId, "");
    }

    private List<String> parseDepartments(String raw) {
        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {
                });
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (trimmed.contains(",")) {
            return List.of(trimmed.split(","));
        }
        if (StringUtils.hasText(trimmed)) {
            return List.of(trimmed);
        }
        return Collections.emptyList();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record ResumeSnapshot(
            String name,
            String firstDepartment,
            String secondDepartment,
            String intendedDepartments,
            String grade,
            String major,
            String selfIntroduction,
            int resumeScore) {

        static ResumeSnapshot empty() {
            return new ResumeSnapshot("", "", "", "", "", "", "", 0);
        }
    }
}
