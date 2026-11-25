package club.boyuan.official.domain.agent.controller;

import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.persistence.mapper.EvaluationScopeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EvaluationQuestionScope {
    private final EvaluationScopeMapper scope;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public void require(long resumeId, int cycleId, Integer scheduleId, boolean write) {
        String username = SecurityUtil.getCurrentUsername();
        if (resumeId < 1 || resumeId > Integer.MAX_VALUE || cycleId < 1)
            throw new AccessDeniedException("候选人无效");
        boolean auditor = scope.boardGrants(username).contains("resume:audit");
        if (auditor && !write && scheduleId == null) return;
        boolean permitted = auditor
                ? scope.matchesSchedule((int) resumeId, cycleId, scheduleId, write)
                : scope.candidate(scope.viewerId(username), (int) resumeId, cycleId, scheduleId, null, write);
        if (!permitted) throw new AccessDeniedException("候选人超出当前账号的评价范围");
    }

    private String key(int cycle, long resume) {
        return "official:qbank:verified:" + cycle + ":" + resume;
    }

    public void remember(int cycle, long resume, ResponseEntity<String> reply) {
        if (!reply.getStatusCode().is2xxSuccessful()) return;
        var key = key(cycle, resume);
        try {
            JsonNode bank = json.readTree(reply.getBody());
            if (bank != null && bank.path("cycle_id").asInt() == cycle
                    && bank.path("resume_id").asLong() == resume && bank.path("pickable").isArray()) {
                redis.opsForValue().set(key, bank.get("pickable").toString(), Duration.ofMinutes(30));
            } else redis.delete(key);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            redis.delete(key);
        }
    }

    public Map<String, Object> validatePick(Map<String, Object> body) {
        JsonNode pick = json.valueToTree(body);
        int resume = id(pick.get("resume_id")), cycle = id(pick.get("cycle_id"));
        Integer schedule = pick.hasNonNull("schedule_id") ? id(pick.get("schedule_id")) : null;
        require(resume, cycle, schedule, true);
        String cached = redis.opsForValue().get(key(cycle, resume));
        if (cached == null) throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "请刷新该候选人的题库");
        JsonNode entries;
        try {
            entries = json.readTree(cached);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "请刷新该候选人的题库");
        }
        JsonNode questions = pick.path("questions");
        if (!questions.isArray() || questions.isEmpty()) throw invalid();
        List<Map<String,String>> verified = new ArrayList<>();
        for (JsonNode question : questions) {
            List<String> matches = new ArrayList<>();
            for (JsonNode entry : entries) {
                if (!entry.hasNonNull("ref_id")) continue;
                boolean matchesEntry = question.hasNonNull("ref_id")
                        ? entry.get("ref_id").equals(question.get("ref_id"))
                        : question.hasNonNull("question") && question.get("question").equals(entry.get("question"))
                        && optionalMatch(question, "category", entry, "category")
                        && optionalMatch(question, "anchor", entry, "category")
                        && optionalMatch(question, "evidence_path", entry, "evidence_path");
                if (matchesEntry) matches.add(entry.get("ref_id").asText());
            }
            if (matches.size() != 1) throw invalid();
            verified.add(Map.of("ref_id", matches.get(0)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resume_id", resume);
        result.put("cycle_id", cycle);
        if (schedule != null) result.put("schedule_id", schedule);
        result.put("questions", verified);
        return result;
    }

    private static boolean optionalMatch(JsonNode q, String field, JsonNode entry, String name) {
        return !q.hasNonNull(field) || q.get(field).asText().isEmpty() || q.get(field).equals(entry.get(name));
    }

    private static int id(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1)
            throw invalid();
        return value.intValue();
    }

    private static BusinessException invalid() {
        return new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "勾选目标无效，请重新打开当前候选人的题库");
    }
}
