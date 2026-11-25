package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CycleInterviewerScopeIntegrationTest {

    private static final int SEEDED_DEPT_ID = 1;
    private static final int INTERVIEWER_USER_ID = 990201;

    @Autowired
    private IEvaluationBoardService evaluationBoardService;

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void accountPermissionOnlyGrantsAnOpenBoard() {
        int open = insertCycle("测试-开放评价板");
        int closed = insertCycle("测试-未开放评价板");
        jdbc.update("INSERT INTO collab_doc(doc_name,cycle_id,locked) VALUES(?,?,0)", "eval-board:" + open, open);
        Integer evaluator = jdbc.queryForObject("SELECT MIN(user_id) FROM evaluation_account_grant WHERE permission_code='interview:evaluate'", Integer.class);
        assertNotNull(evaluator);
        assertTrue(evaluationBoardService.isInterviewerOfCycle(open, evaluator));
        assertFalse(evaluationBoardService.isInterviewerOfCycle(closed, evaluator));
        assertFalse(evaluationBoardService.isInterviewerOfCycle(open, INTERVIEWER_USER_ID));
        assertFalse(evaluationBoardService.isInterviewerOfCycle(null, evaluator));
        assertFalse(evaluationBoardService.isInterviewerOfCycle(open, null));
    }

    private Integer insertCycle(String name) {
        RecruitmentCycle c = new RecruitmentCycle();
        c.setCycleName(name);
        c.setAcademicYear("2099-2100");
        c.setStartDate(LocalDate.now().minusDays(1));
        c.setEndDate(LocalDate.now().plusDays(1));
        c.setIsActive(0);   // 不设为启用，避免影响「当前开放周期」相关的其它测试
        c.setStatus(1);
        cycleMapper.insert(c);
        assertNotNull(c.getCycleId(), "插入周期后应回填主键");
        return c.getCycleId();
    }

}
