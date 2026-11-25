package club.boyuan.official.domain.agent.controller;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.integration.agent.AgentAdminClient;
import club.boyuan.official.persistence.mapper.EvaluationScopeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluationQuestionScopeTest {
    private final EvaluationScopeMapper db = mock(EvaluationScopeMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AgentAdminClient agent = mock(AgentAdminClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private final EvaluationQuestionScope scope = new EvaluationQuestionScope(db, redis, json);
    private final AgentEvaluationProxyController api = new AgentEvaluationProxyController(agent, json, scope);

    EvaluationQuestionScopeTest() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("examiner", null, List.of()));
        when(db.viewerId("examiner")).thenReturn(7);
        when(db.boardGrants("examiner")).thenReturn(List.of("interview:evaluate"));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void outOfRosterRequestsNeverReachAgent() {
        assertThrows(AccessDeniedException.class, () -> api.scorecard("test", 11, 2));
        assertThrows(AccessDeniedException.class, () -> api.qbank("test", 11, 2));
        assertThrows(AccessDeniedException.class, () -> api.pick("test", Map.of(
                "resume_id",11, "cycle_id",2,"schedule_id",9,"questions", List.of(Map.of("ref_id","q1")))));
        verifyNoInteractions(agent,redis);
    }

    @Test void picksUseReferencesFromThisCandidateAndCurrentWritableBoard() {
        when(db.candidate(7,11,2,9,null,true)).thenReturn(true);
        @SuppressWarnings("unchecked") ValueOperations<String,String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("official:qbank:verified:2:11")).thenReturn("[{\"ref_id\":\"q1\",\"question\":\"Example?\"}]");
        var body = Map.<String,Object>of("resume_id",11,"cycle_id",2,"schedule_id",9,"questions",List.of(Map.of("ref_id","q2")));
        assertThrows(BusinessException.class, () -> api.pick("test",body));
        verifyNoInteractions(agent);
        var valid = new java.util.HashMap<>(body);
        valid.put("questions", List.of(Map.of("question","Example?")));
        assertEquals(List.of(Map.of("ref_id","q1")),scope.validatePick(valid).get("questions"));
        when(db.candidate(7,11,2,9,null,true)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> api.pick("test",valid));
        verifyNoInteractions(agent);
    }
}
