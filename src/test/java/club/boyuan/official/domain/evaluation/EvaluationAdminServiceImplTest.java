package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationAdminServiceImplTest {

    @Mock
    private EvaluationSubmissionMapper submissionMapper;
    @Mock
    private UserMapper userMapper;

    private EvaluationAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvaluationAdminServiceImpl(submissionMapper, userMapper);
    }

    @Test
    void submissionsNumericKeyQueriesByUserId() {
        when(submissionMapper.selectByUserId(7)).thenReturn(List.of(new EvaluationSubmission()));
        assertEquals(1, service.submissions("7").size());
        verify(submissionMapper, never()).selectByGithub(anyString());
    }

    @Test
    void submissionsGithubKeyIsNormalized() {
        when(submissionMapper.selectByGithub("alice")).thenReturn(List.of(new EvaluationSubmission()));
        service.submissions("https://github.com/Alice");
        verify(submissionMapper).selectByGithub("alice");
    }

    @Test
    void detailNotFoundThrows() {
        when(submissionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detail(1L));
    }

    @Test
    void claimValidSetsUserId() {
        EvaluationSubmission s = new EvaluationSubmission();
        s.setId(1L);
        when(submissionMapper.selectById(1L)).thenReturn(s);
        when(userMapper.selectById(7)).thenReturn(new User());

        EvaluationSubmission result = service.claim(1L, 7);

        assertEquals(7, result.getUserId());
        verify(submissionMapper).updateById(s);
    }

    @Test
    void claimUserNotFoundThrows() {
        when(userMapper.selectById(7)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.claim(1L, 7));
        verify(submissionMapper, never()).updateById(any(EvaluationSubmission.class));
    }

    @Test
    void claimMissingUserIdThrows() {
        assertThrows(BusinessException.class, () -> service.claim(1L, null));
    }
}