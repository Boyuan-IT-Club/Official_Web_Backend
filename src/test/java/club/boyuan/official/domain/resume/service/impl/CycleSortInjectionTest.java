package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 招募周期分页的排序参数。它们会被 mapper 里的 ${sortBy} ${sortOrder} 原样拼进 SQL，
 * 而接口只要求登录——以前不认识的字段原样放行、方向完全不校验，任何学生都能注入。
 */
class CycleSortInjectionTest {

    private RecruitmentCycleMapper mapper;
    private RecruitmentCycleServiceImpl service;

    private void setUp() {
        mapper = mock(RecruitmentCycleMapper.class);
        when(mapper.findAllWithPaginationAndSorting(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        service = new RecruitmentCycleServiceImpl(mapper, null, null, null, null);
    }

    private String[] sent(String sortBy, String sortOrder) {
        setUp();
        service.getAllRecruitmentCyclesWithPagination(0, 10, sortBy, sortOrder);
        ArgumentCaptor<String> by = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> order = ArgumentCaptor.forClass(String.class);
        verify(mapper).findAllWithPaginationAndSorting(anyInt(), anyInt(), by.capture(), order.capture());
        return new String[] {by.getValue(), order.getValue()};
    }

    @Test
    @DisplayName("不认识的排序字段落回默认列，不原样拼进 SQL")
    void unknownFieldFallsBack() {
        assertEquals("created_at", sent("(SELECT SLEEP(5))", "DESC")[0]);
        assertEquals("created_at", sent("cycle_id; DROP TABLE user", "DESC")[0]);
    }

    @Test
    @DisplayName("白名单字段照常转成列名")
    void knownFieldsMapped() {
        assertEquals("cycle_id", sent("cycleId", "ASC")[0]);
        assertEquals("start_date", sent("startDate", "ASC")[0]);
    }

    @Test
    @DisplayName("方向只认 ASC / DESC")
    void directionNormalized() {
        assertEquals("ASC", sent("cycleId", "asc")[1]);
        assertEquals("DESC", sent("cycleId", "DESC, (SELECT SLEEP(5))")[1]);
        assertEquals("DESC", sent("cycleId", null)[1]);
    }
}
