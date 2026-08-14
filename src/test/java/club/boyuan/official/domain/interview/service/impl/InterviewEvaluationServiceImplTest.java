package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationRequestDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationResultDTO;
import club.boyuan.official.persistence.entity.CollabAudit;
import club.boyuan.official.persistence.entity.CollabDoc;
import club.boyuan.official.persistence.entity.EvaluationDimension;
import club.boyuan.official.persistence.entity.InterviewEvaluation;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.mapper.CollabAuditMapper;
import club.boyuan.official.persistence.mapper.CollabDocMapper;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.EvaluationDimensionMapper;
import club.boyuan.official.persistence.mapper.InterviewEvaluationMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物化回写的边界行为：越权丢弃、锁定拒绝、加权总分。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewEvaluationServiceImplTest {

    private static final Integer CYCLE_ID = 3;
    private static final String DOC_NAME = "eval-board:3";

    @Mock private InterviewEvaluationMapper interviewEvaluationMapper;
    @Mock private InterviewScheduleMapper interviewScheduleMapper;
    @Mock private EvaluationDimensionMapper evaluationDimensionMapper;
    @Mock private SessionInterviewerMapper sessionInterviewerMapper;
    @Mock private CollabDocMapper collabDocMapper;
    @Mock private CollabAuditMapper collabAuditMapper;
    @Mock private ResumeMapper resumeMapper;
    @Mock private UserMapper userMapper;
    @Mock private DepartmentMapper departmentMapper;

    private InterviewEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewEvaluationServiceImpl(
                interviewEvaluationMapper, interviewScheduleMapper, evaluationDimensionMapper,
                sessionInterviewerMapper, collabDocMapper, collabAuditMapper,
                resumeMapper, userMapper, departmentMapper, new ObjectMapper());

        when(collabDocMapper.selectById(DOC_NAME)).thenReturn(
                new CollabDoc().setDocName(DOC_NAME).setCycleId(CYCLE_ID).setLocked(0));
        when(evaluationDimensionMapper.selectList(any())).thenReturn(List.of(
                dimension(1, "技术能力", new BigDecimal("2.00")),
                dimension(2, "沟通表达", new BigDecimal("1.00"))));
        when(interviewScheduleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                schedule(100, CYCLE_ID), schedule(101, CYCLE_ID)));
    }

    @Test
    void materialize_写入他人评分的条目被丢弃且不影响同批次合法数据() {
        MaterializeEvaluationRequestDTO request = request(
                item(100, 7, 7, Map.of(1, new BigDecimal("8")), 1000L),
                // 用户 7 试图写入面试官 9 的格子
                item(101, 9, 7, Map.of(1, new BigDecimal("10")), 1000L));

        MaterializeEvaluationResultDTO result = service.materialize(request);

        assertEquals(1, result.getAccepted());
        assertEquals(1, result.getRejected());
        assertTrue(result.getRejectReasons().get(0).contains("试图写入面试官 9"));

        ArgumentCaptor<InterviewEvaluation> captor = ArgumentCaptor.forClass(InterviewEvaluation.class);
        verify(interviewEvaluationMapper, times(1)).upsertMaterialized(captor.capture());
        assertEquals(100, captor.getValue().getScheduleId());
        assertEquals(7, captor.getValue().getInterviewerUserId());
    }

    @Test
    void materialize_越权条目以rejected标记写入审计() {
        service.materialize(request(item(101, 9, 7, Map.of(1, new BigDecimal("10")), 1000L)));

        ArgumentCaptor<CollabAudit> captor = ArgumentCaptor.forClass(CollabAudit.class);
        verify(collabAuditMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getRejected());
        assertEquals(7, captor.getValue().getUserId());
        assertEquals(DOC_NAME, captor.getValue().getDocName());
    }

    @Test
    void materialize_加权总分按维度权重累加() {
        service.materialize(request(item(100, 7, 7,
                Map.of(1, new BigDecimal("8"), 2, new BigDecimal("6")), 1000L)));

        ArgumentCaptor<InterviewEvaluation> captor = ArgumentCaptor.forClass(InterviewEvaluation.class);
        verify(interviewEvaluationMapper).upsertMaterialized(captor.capture());
        // 8×2.00 + 6×1.00 = 22.00
        assertEquals(0, new BigDecimal("22.00").compareTo(captor.getValue().getTotalScore()));
    }

    @Test
    void materialize_不在本周期维度表里的分数不计入总分但仍保留在scores中() {
        service.materialize(request(item(100, 7, 7,
                Map.of(1, new BigDecimal("8"), 99, new BigDecimal("5")), 1000L)));

        ArgumentCaptor<InterviewEvaluation> captor = ArgumentCaptor.forClass(InterviewEvaluation.class);
        verify(interviewEvaluationMapper).upsertMaterialized(captor.capture());
        assertEquals(0, new BigDecimal("16.00").compareTo(captor.getValue().getTotalScore()));
        assertTrue(captor.getValue().getScores().contains("99"));
    }

    @Test
    void materialize_评价表锁定后整批拒绝() {
        when(collabDocMapper.selectById(DOC_NAME)).thenReturn(
                new CollabDoc().setDocName(DOC_NAME).setCycleId(CYCLE_ID).setLocked(1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.materialize(request(item(100, 7, 7, Map.of(), 1000L))));

        assertEquals(BusinessExceptionEnum.EVALUATION_BOARD_LOCKED.getCode(), exception.getCode());
        verify(interviewEvaluationMapper, never()).upsertMaterialized(any());
    }

    @Test
    void materialize_文档名与周期不匹配时拒绝() {
        MaterializeEvaluationRequestDTO request = request(item(100, 7, 7, Map.of(), 1000L));
        request.setDocName("eval-board:999");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.materialize(request));

        assertEquals(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED.getCode(), exception.getCode());
    }

    @Test
    void materialize_面试安排不属于本周期时丢弃() {
        when(interviewScheduleMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(schedule(100, 999)));

        MaterializeEvaluationResultDTO result = service.materialize(
                request(item(100, 7, 7, Map.of(1, new BigDecimal("8")), 1000L)));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getRejected());
        verify(interviewEvaluationMapper, never()).upsertMaterialized(any());
    }

    @Test
    void materialize_未开表时拒绝() {
        when(collabDocMapper.selectById(DOC_NAME)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.materialize(request(item(100, 7, 7, Map.of(), 1000L))));

        assertEquals(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED.getCode(), exception.getCode());
    }

    private MaterializeEvaluationRequestDTO request(MaterializeEvaluationRequestDTO.EvaluationItem... items) {
        MaterializeEvaluationRequestDTO request = new MaterializeEvaluationRequestDTO();
        request.setDocName(DOC_NAME);
        request.setCycleId(CYCLE_ID);
        request.setItems(new ArrayList<>(List.of(items)));
        return request;
    }

    private MaterializeEvaluationRequestDTO.EvaluationItem item(Integer scheduleId,
                                                                Integer interviewerUserId,
                                                                Integer originUserId,
                                                                Map<Integer, BigDecimal> scores,
                                                                Long version) {
        MaterializeEvaluationRequestDTO.EvaluationItem item = new MaterializeEvaluationRequestDTO.EvaluationItem();
        item.setScheduleId(scheduleId);
        item.setInterviewerUserId(interviewerUserId);
        item.setOriginUserId(originUserId);
        item.setScores(scores);
        item.setVersion(version);
        return item;
    }

    private InterviewSchedule schedule(Integer scheduleId, Integer cycleId) {
        return new InterviewSchedule()
                .setScheduleId(scheduleId)
                .setCycleId(cycleId)
                .setResumeId(scheduleId + 900)
                .setUserId(scheduleId + 800);
    }

    private EvaluationDimension dimension(Integer id, String name, BigDecimal weight) {
        return new EvaluationDimension()
                .setDimensionId(id)
                .setCycleId(CYCLE_ID)
                .setName(name)
                .setMaxScore(10)
                .setWeight(weight)
                .setSortOrder(id);
    }
}
