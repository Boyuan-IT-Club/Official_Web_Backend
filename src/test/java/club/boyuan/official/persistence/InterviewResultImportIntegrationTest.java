package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.InterviewFeishuImportService;
import club.boyuan.official.integration.feishu.FeishuApiInvoker;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"feishu.app-id=fixture-id", "feishu.app-secret=fixture-secret", "feishu.list-page-size=1"})
@Transactional
class InterviewResultImportIntegrationTest {
    @MockBean FeishuApiInvoker remote;
    @Autowired InterviewFeishuImportService tasks;
    @Autowired RecruitmentCycleMapper cycles;
    @Autowired UserMapper users;
    @Autowired ResumeMapper resumes;
    @Autowired InterviewScheduleMapper schedules;
    @Autowired JdbcTemplate jdbc;

    final ImportFromFeishuTableRequestDTO input = new ImportFromFeishuTableRequestDTO();
    int scheduleId;
    static final String FIRST = """
        {"code":0,"data":{"has_more":true,"page_token":"page2","items":[{"record_id":"r1","fields":{"姓名":"导入夹具","面试是否通过":false}}]}}
        """;
    static final String LAST = """
        {"code":0,"data":{"has_more":false,"items":[{"record_id":"r2","fields":{"姓名":"不在本周期的夹具"}}]}}
        """;

    @BeforeEach
    void prepare() {
        var cycle = new RecruitmentCycle();
        cycle.setCycleName("导入测试");
        cycle.setAcademicYear("2099-2100");
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusDays(1));
        cycle.setIsActive(0);
        cycles.insert(cycle);
        var user = new User();
        String login = "feishu_test_" + System.nanoTime();
        user.setUsername(login);
        user.setPassword("fixture");
        user.setEmail(login + "@example.invalid");
        user.setName("导入夹具");
        users.insert(user);
        var resume = new Resume();
        resume.setCycleId(cycle.getCycleId());
        resume.setUserId(user.getUserId());
        resume.setStatus(2);
        resumes.insert(resume);
        var schedule = new InterviewSchedule().setCycleId(cycle.getCycleId()).setUserId(user.getUserId())
                .setResumeId(resume.getResumeId()).setStatus(1);
        schedules.insert(schedule);
        scheduleId = schedule.getScheduleId();
        input.setCycleId(cycle.getCycleId());
        input.setUpdateUserDept(false);
        input.setFeishuTableUrl("https://fixture.feishu.cn/base/AppFixture?table=tblFixture");
        when(remote.postJson(anyString(), isNull(), any())).thenReturn("{\"code\":0,\"tenant_access_token\":\"fixture\",\"expire\":3600}");
        when(remote.get(endsWith("?page_size=1"), eq("fixture"))).thenReturn(FIRST);
    }

    @Test
    void multiplePagesKeepTheSameTaskAndRepeatedImportUpdatesOneResult() {
        when(remote.get(endsWith("&page_token=page2"), eq("fixture"))).thenReturn(LAST);
        for (int i = 0; i < 2; i++) {
            long task = tasks.submitPullFromTableTask(input, 1).getTaskId();
            tasks.runImportTask(task);
            var status = tasks.getImportTaskStatus(task);
            assertEquals(task, status.getTaskId());
            assertEquals("PARTIAL_SUCCESS", status.getStatus());
            assertEquals(100, status.getProgressPercent());
            assertEquals(1, status.getImportedCount());
            assertEquals(1, status.getFailedCount());
            assertEquals("r1", status.getPullResult().getRows().get(0).getRecordId());
            assertTrue(status.getPullResult().getRows().get(0).isSuccess());
            assertFalse(status.getPullResult().getRows().get(1).isSuccess());
            tasks.runImportTask(task);
            assertEquals(1, countResults());
        }
        verify(remote, times(2)).get(endsWith("&page_token=page2"), eq("fixture"));
    }

    @Test
    void aTransportFailureOnPageTwoLeavesTheTaskQueryableAndImportsNoPartialPage() {
        when(remote.get(endsWith("&page_token=page2"), eq("fixture")))
                .thenThrow(new IllegalStateException("fixture page unavailable"));
        long task = tasks.submitPullFromTableTask(input, 1).getTaskId();
        assertThrows(IllegalStateException.class, () -> tasks.runImportTask(task));
        var status = tasks.getImportTaskStatus(task);
        assertEquals(task, status.getTaskId());
        assertEquals("FAILED", status.getStatus());
        assertTrue(status.getErrorMessage().contains("fixture page unavailable"));
        assertEquals(0, countResults());
    }

    private int countResults() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM interview_result WHERE schedule_id=?", Integer.class, scheduleId);
    }
}
