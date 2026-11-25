package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.persistence.mapper.EvaluationScopeMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewerMaterialScopeTest {
    @Test
    void authorizesOnlyTheResumePresentOnTheBoard() {
        var mapper = mock(EvaluationScopeMapper.class);
        when(mapper.candidate(9, 31, null, null, null, false)).thenReturn(true);
        var scope = new InterviewerMaterialScope(mapper);
        assertTrue(scope.canReadResume(9, 31));
        assertFalse(scope.canReadResume(9, 32));
        assertFalse(scope.canReadResume(null, 31));
    }
}
