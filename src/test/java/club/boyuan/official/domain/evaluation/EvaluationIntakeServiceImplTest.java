package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.domain.evaluation.dto.EvaluationIntakeRequest;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationIntakeServiceImplTest {

    @Mock
    private EvaluationSubmissionMapper submissionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RecruitmentCycleMapper cycleMapper;

    private EvaluationIntakeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvaluationIntakeServiceImpl(submissionMapper, userMapper, cycleMapper);
    }

    private EvaluationIntakeRequest validRequest() throws Exception {
        EvaluationIntakeRequest req = new EvaluationIntakeRequest();
        req.setReport(EvaluationTestData.encryptEnvelopeB64(EvaluationTestData.sampleReportJson()));
        req.setGithubUsername("https://github.com/Alice");
        req.setRepository("github.com/alice/interview-autograding-template");
        req.setCommitSha("abc123");
        return req;
    }

    @Test
    void ingestValidMatchesUserAndActiveCycle() throws Exception {
        User user = new User();
        user.setUserId(7);
        when(userMapper.selectOne(any())).thenReturn(user);

        RecruitmentCycle cycle = new RecruitmentCycle();
        cycle.setCycleId(3);
        when(cycleMapper.findByIsActive(1)).thenReturn(List.of(cycle));

        when(submissionMapper.selectOne(any())).thenReturn(null);
        when(submissionMapper.insert(any(EvaluationSubmission.class))).thenReturn(1);

        service.ingest(validRequest());

        ArgumentCaptor<EvaluationSubmission> captor = ArgumentCaptor.forClass(EvaluationSubmission.class);
        verify(submissionMapper).insert(captor.capture());
        EvaluationSubmission s = captor.getValue();

        assertEquals("alice", s.getGithubUsername()); // URL 归一化为登录名,小写
        assertEquals(7, s.getUserId());
        assertEquals(3, s.getCycleId());
        assertEquals(340, s.getTotalScore());
        assertEquals(400, s.getMaxScore());
        assertEquals(100, s.getTask1Score());
        assertEquals(80, s.getTask2Score());
        assertEquals(90, s.getTask3Score());
        assertEquals(70, s.getTask4Score());
        assertEquals(64, s.getReportSha().length());
        assertEquals("alice", s.getAuthor());
        assertTrue(s.getReportJson().contains("\"total_score\":340"));
    }

    @Test
    void ingestWithoutMatchLeavesUserAndCycleNull() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(cycleMapper.findByIsActive(1)).thenReturn(Collections.emptyList());
        when(submissionMapper.selectOne(any())).thenReturn(null);
        when(submissionMapper.insert(any(EvaluationSubmission.class))).thenReturn(1);

        service.ingest(validRequest());

        ArgumentCaptor<EvaluationSubmission> captor = ArgumentCaptor.forClass(EvaluationSubmission.class);
        verify(submissionMapper).insert(captor.capture());
        assertNull(captor.getValue().getUserId());
        assertNull(captor.getValue().getCycleId());
    }

    @Test
    void ingestDeduplicatesBySha() throws Exception {
        EvaluationSubmission existing = new EvaluationSubmission();
        existing.setId(99L);
        when(submissionMapper.selectOne(any())).thenReturn(existing);

        EvaluationSubmission result = service.ingest(validRequest());

        assertEquals(99L, result.getId());
        verify(submissionMapper, never()).insert(any(EvaluationSubmission.class));
    }

    @Test
    void ingestRejectsInvalidBase64() {
        EvaluationIntakeRequest req = new EvaluationIntakeRequest();
        req.setReport("!!!not-base64!!!");
        req.setGithubUsername("alice");
        assertThrows(BusinessException.class, () -> service.ingest(req));
    }

    @Test
    void ingestRejectsMissingGithub() throws Exception {
        EvaluationIntakeRequest req = validRequest();
        req.setGithubUsername("   ");
        assertThrows(BusinessException.class, () -> service.ingest(req));
    }

    @Test
    void ingestRejectsMissingReport() {
        EvaluationIntakeRequest req = new EvaluationIntakeRequest();
        req.setGithubUsername("alice");
        assertThrows(BusinessException.class, () -> service.ingest(req));
    }

    @Test
    void ingestRejectsOversizedReport() {
        EvaluationIntakeRequest req = new EvaluationIntakeRequest();
        req.setGithubUsername("alice");
        req.setReport("A".repeat(EvaluationIntakeServiceImpl.MAX_REPORT_BASE64_LENGTH + 1));

        assertThrows(BusinessException.class, () -> service.ingest(req));
        verify(submissionMapper, never()).insert(any(EvaluationSubmission.class));
    }
}