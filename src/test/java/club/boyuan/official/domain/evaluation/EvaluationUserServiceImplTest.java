package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.evaluation.dto.TrendPoint;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationUserServiceImplTest {

    @Mock
    private EvaluationSubmissionMapper submissionMapper;

    private EvaluationUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvaluationUserServiceImpl(submissionMapper);
    }

    @Test
    void pageWrapsResult() {
        EvaluationSubmission s = new EvaluationSubmission();
        s.setId(1L);
        when(submissionMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<EvaluationSubmission> p = inv.getArgument(0);
            p.setRecords(List.of(s));
            p.setTotal(1);
            return p;
        });

        PageResultDTO<EvaluationSubmission> result = service.page(7, 0, 10);

        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void latestReturnsNullWhenNone() {
        when(submissionMapper.selectOne(any())).thenReturn(null);
        assertNull(service.latest(7));
    }

    @Test
    void latestReturnsRow() {
        EvaluationSubmission s = new EvaluationSubmission();
        s.setId(2L);
        when(submissionMapper.selectOne(any())).thenReturn(s);
        assertEquals(2L, service.latest(7).getId());
    }

    @Test
    void trendMapsToAscendingPoints() {
        EvaluationSubmission a = new EvaluationSubmission();
        a.setEvaluatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        a.setTotalScore(200);
        EvaluationSubmission b = new EvaluationSubmission();
        b.setEvaluatedAt(LocalDateTime.of(2026, 8, 2, 10, 0));
        b.setTotalScore(340);
        when(submissionMapper.selectList(any())).thenReturn(List.of(a, b));

        List<TrendPoint> points = service.trend(7);

        assertEquals(2, points.size());
        assertEquals(200, points.get(0).getTotalScore());
        assertEquals(340, points.get(1).getTotalScore());
    }
}