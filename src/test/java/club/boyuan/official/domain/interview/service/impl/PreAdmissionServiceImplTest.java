package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.domain.interview.dto.PreAdmissionBatchRequestDTO;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.persistence.mapper.PreAdmissionDraftMapper;
import club.boyuan.official.domain.user.service.IUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreAdmissionServiceImplTest {

    @Mock private PreAdmissionDraftMapper draftMapper;
    @Mock private InterviewResultMapper resultMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private IUserService userService;
    @InjectMocks private PreAdmissionServiceImpl service;

    @Test
    void 加入名单只接受本周期尚未定稿的人并可批量换部门() {
        Department department = new Department();
        department.setDeptId(2);
        department.setStatus(1);
        when(departmentMapper.selectById(2)).thenReturn(department);
        when(resultMapper.selectPendingResultIdsInCycle(7, List.of(10, 11, 12)))
                .thenReturn(List.of(10, 12));

        PreAdmissionBatchRequestDTO request = new PreAdmissionBatchRequestDTO();
        request.setCycleId(7);
        request.setResultIds(List.of(10, 11, 10, 12));
        request.setAssignedDeptId(2);

        var response = service.save(request);

        assertEquals(2, response.getAffected());
        assertEquals(List.of(11), response.getSkipped());
        verify(draftMapper).upsertBatch(eq(7), eq(List.of(10, 12)), eq(2), nullable(Integer.class));
    }

    @Test
    void 转正将整份草稿写入最终结果后清空草稿() {
        List<PreAdmissionDraft> drafts = List.of(
                new PreAdmissionDraft().setResultId(10),
                new PreAdmissionDraft().setResultId(12));
        when(draftMapper.selectForFinalize(7)).thenReturn(drafts);
        when(draftMapper.publishToResults(eq(7), nullable(Integer.class))).thenReturn(2);

        assertEquals(2, service.finalizeCycle(7));

        verify(draftMapper).deleteBatch(7, List.of(10, 12));
    }

    @Test
    void 转正遇到已从其他入口定稿的人则整批拒绝且不清草稿() {
        when(draftMapper.selectForFinalize(7)).thenReturn(List.of(
                new PreAdmissionDraft().setResultId(10),
                new PreAdmissionDraft().setResultId(12)));
        when(draftMapper.publishToResults(eq(7), nullable(Integer.class))).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.finalizeCycle(7));
        verify(draftMapper, never()).deleteBatch(any(), any());
    }
}
