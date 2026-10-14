package club.boyuan.official.persistence;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.PreAdmissionListResponseDTO;
import club.boyuan.official.domain.interview.dto.ResultSummaryDTO;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.PreAdmissionService;
import club.boyuan.official.domain.interview.service.ResultRosterService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.domain.user.service.DepartmentService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.integration.feishu.FeishuBitableClient;
import club.boyuan.official.integration.feishu.FeishuBitableRecord;
import club.boyuan.official.integration.feishu.FeishuTablePullImportExecutor;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuRowResultDTO;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableResponseDTO;
import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import club.boyuan.official.persistence.mapper.InterviewResultChangeMapper;
import club.boyuan.official.persistence.mapper.PreAdmissionDraftMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 录取流程第一批：名单自动补齐、服务端录取统计、预录取转正前点名冲突、飞书拉回不覆盖已定结果。
 */
@SpringBootTest
class ResultRosterSummaryIntegrationTest {

    /** V6 种子：部门 1 = 技术部、2 = 综合部 */
    private static final int DEPT_TECH = 1;
    private static final int DEPT_GENERAL = 2;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ResultRosterService rosterService;
    @Autowired private IInterviewResultService resultService;
    @Autowired private PreAdmissionService preAdmissionService;

    @Autowired private IUserService userService;
    @Autowired private ResumeMapper resumeMapper;
    @Autowired private IInterviewScheduleService scheduleService;
    @Autowired private DepartmentService departmentService;
    @Autowired private IRecruitmentCycleService cycleService;
    @Autowired private PreAdmissionDraftMapper draftMapper;
    @Autowired private InterviewResultChangeMapper changeMapper;

    private Integer cycleId;
    private final List<Integer> userIds = new ArrayList<>();

    @Test
    @DisplayName("名单自动补齐：过了初筛的人各一行，重复调用不多建；只挂了面试安排的老数据行不会被重复补")
    void rosterEnsureIsIdempotent() {
        setUpCycle();
        int scheduled = resume("SCH", 2);
        schedule(scheduled);
        int unscheduled = resume("UNS", 3);
        resume("SCR", 5);          // 初筛未通过：不进名单
        resume("DRAFT", 1);        // 还没提交：不进名单
        int legacy = resume("LEG", 2);
        int legacySchedule = schedule(legacy);
        // 飞书拉回建的老行：只有 schedule_id，没有 cycle_id / resume_id
        insert("INSERT INTO interview_result (schedule_id, user_id, decision) VALUES (?, ?, 0)",
                legacySchedule, userOf(legacy));
        int existing = resume("HAS", 2);
        insert("INSERT INTO interview_result (resume_id, cycle_id, user_id, decision) VALUES (?, ?, ?, 1)",
                existing, cycleId, userOf(existing));

        assertEquals(2, rosterService.ensure(cycleId));
        assertEquals(0, rosterService.ensure(cycleId), "第二次调用不能再建");

        assertEquals(1, count("SELECT COUNT(*) FROM interview_result WHERE resume_id = ?", scheduled));
        assertNotNull(jdbc.queryForObject("SELECT schedule_id FROM interview_result WHERE resume_id = ?",
                Integer.class, scheduled), "有生效安排的挂上安排");
        assertEquals(1, count("SELECT COUNT(*) FROM interview_result WHERE resume_id = ?", unscheduled));
        assertEquals(1, count("SELECT COUNT(*) FROM interview_result WHERE schedule_id = ?", legacySchedule));
        assertEquals(0, count("SELECT COUNT(*) FROM interview_result WHERE resume_id = ?", legacy),
                "老行按安排认出来了，不能再按简历补一行");
        assertEquals(1, count("SELECT COUNT(*) FROM interview_result WHERE resume_id = ?", existing));
        assertEquals(4, count("SELECT COUNT(*) FROM interview_result ir"
                + " LEFT JOIN interview_schedule s ON s.schedule_id = ir.schedule_id"
                + " WHERE COALESCE(ir.cycle_id, s.cycle_id) = ?", cycleId));
    }

    @Test
    @DisplayName("录取统计：总数与各部门的志愿、预录取、录取（一志愿/二志愿/调剂）、已通知；初筛未通过的待定不计")
    void summaryCountsByDepartment() {
        setUpCycle();
        candidate("X", 2, 1, DEPT_TECH, false, DEPT_TECH, null);             // 一志愿录取
        candidate("Y", 2, 1, DEPT_TECH, false, DEPT_GENERAL, DEPT_TECH);     // 二志愿录取
        candidate("Z", 2, 1, DEPT_GENERAL, true, DEPT_TECH, null);           // 调剂录取，已通知
        int w = candidate("W", 2, 0, null, false, DEPT_GENERAL, null);       // 待定，在预录取
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?)",
                cycleId, w, DEPT_GENERAL);
        candidate("V", 2, 2, null, false, DEPT_TECH, null);                  // 未通过
        candidate("S", 5, 0, null, false, DEPT_TECH, null);                  // 初筛未通过且待定：不计

        ResultSummaryDTO summary = resultService.summary(cycleId);

        ResultSummaryDTO.Totals t = summary.getTotals();
        assertEquals(5, t.getCandidates());
        assertEquals(1, t.getPending());
        assertEquals(0, t.getAdjusting());
        assertEquals(3, t.getAdmitted());
        assertEquals(1, t.getRejected());
        assertEquals(1, t.getInPreAdmission());
        assertEquals(1, t.getNotified());

        ResultSummaryDTO.DeptRow tech = dept(summary, DEPT_TECH);
        assertEquals(3, tech.getFirstChoice(), "X、Z、V；初筛未通过的 S 不算");
        assertEquals(1, tech.getSecondChoice());
        assertEquals(0, tech.getPreAdmitted());
        assertEquals(2, tech.getAdmitted());
        assertEquals(1, tech.getAdmittedFirstChoice());
        assertEquals(1, tech.getAdmittedSecondChoice());
        assertEquals(0, tech.getAdmittedOther());
        assertEquals(0, tech.getAdmittedNotified());

        ResultSummaryDTO.DeptRow general = dept(summary, DEPT_GENERAL);
        assertEquals(2, general.getFirstChoice());
        assertEquals(0, general.getSecondChoice());
        assertEquals(1, general.getPreAdmitted());
        assertEquals(1, general.getAdmitted());
        assertEquals(1, general.getAdmittedOther(), "两个志愿都不是综合部：算调剂");
        assertEquals(1, general.getAdmittedNotified());
    }

    @Test
    @DisplayName("预录取转正：名单里有人已不是待定时先点名、一个都不发布；移出后转正，没进名单的待定保持待定")
    void finalizeNamesConflictsFirst() {
        setUpCycle();
        int ok = candidate("OK", 2, 0, null, false, DEPT_TECH, null);
        int clash = candidate("CLASH", 2, 0, null, false, DEPT_TECH, null);
        int untouched = candidate("STAY", 2, 0, null, false, DEPT_TECH, null);
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?), (?, ?, ?)",
                cycleId, ok, DEPT_TECH, cycleId, clash, DEPT_TECH);
        // 历史数据：进了名单之后又被别的入口定成了未通过
        jdbc.update("UPDATE interview_result SET decision = 2 WHERE result_id = ?", clash);

        PreAdmissionListResponseDTO list = preAdmissionService.list(cycleId, null, null, 1, 20);
        PreAdmissionDraft clashRow = list.getCandidates().stream()
                .filter(d -> d.getResultId() == clash).findFirst().orElseThrow();
        assertEquals(2, clashRow.getDecision(), "名单要带出当前结果，前端据此标冲突");

        BusinessException ex = assertThrows(BusinessException.class, () -> preAdmissionService.finalizeCycle(cycleId));
        assertEquals(BusinessExceptionEnum.RESOURCE_CONFLICT.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("候选人CLASH"), ex.getMessage());
        assertEquals(0, decisionOf(ok), "有冲突时一个都不发布");
        assertEquals(2, count("SELECT COUNT(*) FROM pre_admission_draft WHERE cycle_id = ?", cycleId));

        jdbc.update("DELETE FROM pre_admission_draft WHERE result_id = ?", clash);
        assertEquals(1, preAdmissionService.finalizeCycle(cycleId));
        assertEquals(1, decisionOf(ok));
        assertEquals(DEPT_TECH, count("SELECT assigned_dept_id FROM interview_result WHERE result_id = ?", ok));
        Map<String, Object> change = jdbc.queryForMap(
                "SELECT * FROM interview_result_change WHERE result_id = ? AND action = 'FINALIZE'", ok);
        assertEquals(0, ((Number) change.get("from_decision")).intValue());
        assertEquals(1, ((Number) change.get("to_decision")).intValue());
        assertEquals(DEPT_TECH, ((Number) change.get("to_dept_id")).intValue());
        assertEquals(0, decisionOf(untouched), "没进名单的待定保持待定");
        assertEquals(2, decisionOf(clash));
    }

    @Test
    @DisplayName("飞书拉回：已定且不同、在预录取名单里的不覆盖；内容相同算成功；待定的照写并留痕、补齐老行的周期与简历")
    void feishuPullRespectsLock() {
        setUpCycle();
        int rejected = candidate("FREJ", 2, 2, null, true, DEPT_TECH, null);
        int admitted = candidate("FADM", 2, 1, DEPT_TECH, false, DEPT_TECH, null);
        int drafted = candidate("FDRF", 2, 0, null, false, DEPT_TECH, null);
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?)",
                cycleId, drafted, DEPT_TECH);
        int pending = candidate("FPEN", 2, 0, null, false, DEPT_TECH, null);
        int legacyResume = resume("FLEG", 2);
        int legacySchedule = schedule(legacyResume);
        int legacy = insert("INSERT INTO interview_result (schedule_id, user_id, decision) VALUES (?, ?, 0)",
                legacySchedule, userOf(legacyResume));

        String tech = jdbc.queryForObject("SELECT dept_name FROM department WHERE dept_id = ?", String.class, DEPT_TECH);
        FeishuBitableClient client = mock(FeishuBitableClient.class);
        when(client.listAllRecords("https://example.feishu.cn/base/x")).thenReturn(List.of(
                row("r1", "候选人FREJ", tech, true),     // 已婉拒 → 飞书说通过：不覆盖
                row("r2", "候选人FADM", tech, true),     // 已录取且一致：成功、不留痕
                row("r3", "候选人FDRF", tech, true),     // 在预录取：不覆盖
                row("r4", "候选人FPEN", tech, false),    // 待定 → 未通过：写入，部门清空
                row("r5", "候选人FLEG", tech, true)));   // 老行 → 通过：写入并补齐周期与简历
        FeishuTablePullImportExecutor executor = new FeishuTablePullImportExecutor(client, userService, resumeMapper,
                scheduleService, resultService, departmentService, cycleService, rosterService, draftMapper, changeMapper);
        ImportFromFeishuTableRequestDTO req = new ImportFromFeishuTableRequestDTO();
        req.setCycleId(cycleId);
        req.setFeishuTableUrl("https://example.feishu.cn/base/x");
        req.setUpdateUserDept(true);

        ImportFromFeishuTableResponseDTO res = executor.execute(req, null);

        Map<String, ImportFromFeishuRowResultDTO> rows = new java.util.HashMap<>();
        res.getRows().forEach(r -> rows.put(r.getRecordId(), r));
        assertFalse(rows.get("r1").isSuccess());
        assertTrue(rows.get("r1").getMessage().contains("已有正式结果"), rows.get("r1").getMessage());
        assertEquals(2, decisionOf(rejected));
        assertNull(jdbc.queryForObject("SELECT u.dept_id FROM user u JOIN interview_result ir ON ir.user_id = u.user_id"
                + " WHERE ir.result_id = ?", Integer.class, rejected), "没写进结果的行，不能顺手改了人的部门");

        assertTrue(rows.get("r2").isSuccess());
        assertEquals(0, count("SELECT COUNT(*) FROM interview_result_change WHERE result_id = ?", admitted));

        assertFalse(rows.get("r3").isSuccess());
        assertTrue(rows.get("r3").getMessage().contains("预录取"), rows.get("r3").getMessage());
        assertEquals(0, decisionOf(drafted));

        assertTrue(rows.get("r4").isSuccess(), rows.get("r4").getMessage());
        assertEquals(2, decisionOf(pending));
        assertNull(jdbc.queryForObject("SELECT assigned_dept_id FROM interview_result WHERE result_id = ?",
                Integer.class, pending), "未通过不留部门");
        assertEquals(1, count("SELECT COUNT(*) FROM interview_result_change WHERE result_id = ? AND action = 'FEISHU'"
                + " AND from_decision = 0 AND to_decision = 2", pending));

        assertTrue(rows.get("r5").isSuccess(), rows.get("r5").getMessage());
        Map<String, Object> legacyRow = jdbc.queryForMap("SELECT * FROM interview_result WHERE result_id = ?", legacy);
        assertEquals(1, ((Number) legacyRow.get("decision")).intValue());
        assertEquals(cycleId, ((Number) legacyRow.get("cycle_id")).intValue());
        assertEquals(legacyResume, ((Number) legacyRow.get("resume_id")).intValue());
        assertEquals(1, count("SELECT COUNT(*) FROM interview_result WHERE schedule_id = ? OR resume_id = ?",
                legacySchedule, legacyResume), "拉回前补名单不能给老数据的人再建一行");
    }

    @AfterEach
    void cleanup() {
        if (cycleId != null) {
            jdbc.update("DELETE FROM interview_result_change WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM pre_admission_draft WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM interview_result WHERE cycle_id = ? OR schedule_id IN"
                    + " (SELECT schedule_id FROM interview_schedule WHERE cycle_id = ?)", cycleId, cycleId);
            jdbc.update("DELETE FROM interview_schedule WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM interview_preference WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM resume WHERE cycle_id = ?", cycleId);
        }
        userIds.forEach(id -> jdbc.update("DELETE FROM user WHERE user_id = ?", id));
        if (cycleId != null) {
            jdbc.update("DELETE FROM recruitment_cycle WHERE cycle_id = ?", cycleId);
        }
    }

    // ── 测试数据 ──

    private void setUpCycle() {
        cycleId = insert("INSERT INTO recruitment_cycle (cycle_name, start_date, end_date, academic_year, status, is_active)"
                        + " VALUES (?, ?, ?, '2099-2100', 2, 1)",
                "roster" + System.nanoTime(), LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
    }

    private int resume(String who, int status) {
        String username = "rs" + System.nanoTime() + who;
        int userId = insert("INSERT INTO user (username, password, email, name) VALUES (?, 'x', ?, ?)",
                username, username + "@stu.ecnu.edu.cn", "候选人" + who);
        userIds.add(userId);
        return insert("INSERT INTO resume (user_id, cycle_id, status) VALUES (?, ?, ?)", userId, cycleId, status);
    }

    private int schedule(int resumeId) {
        return insert("INSERT INTO interview_schedule (resume_id, user_id, cycle_id, status) VALUES (?, ?, ?, 1)",
                resumeId, userOf(resumeId), cycleId);
    }

    /** 一个有志愿、有结果行的候选人，返回 result_id */
    private int candidate(String who, int resumeStatus, int decision, Integer deptId, boolean notified,
                          Integer firstDept, Integer secondDept) {
        int resumeId = resume(who, resumeStatus);
        jdbc.update("INSERT INTO interview_preference (resume_id, cycle_id, first_dept_id, second_dept_id)"
                + " VALUES (?, ?, ?, ?)", resumeId, cycleId, firstDept, secondDept);
        int resultId = insert("INSERT INTO interview_result (resume_id, cycle_id, user_id, decision, assigned_dept_id)"
                + " VALUES (?, ?, ?, ?, ?)", resumeId, cycleId, userOf(resumeId), decision, deptId);
        if (notified) {
            jdbc.update("UPDATE interview_result SET notified_at = NOW() WHERE result_id = ?", resultId);
        }
        return resultId;
    }

    private static FeishuBitableRecord row(String recordId, String name, String dept, boolean passed) {
        return new FeishuBitableRecord(recordId, null, name, dept, passed, null, null, null);
    }

    private static ResultSummaryDTO.DeptRow dept(ResultSummaryDTO summary, int deptId) {
        return summary.getDepartments().stream()
                .filter(d -> d.getDeptId() == deptId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("统计里没有部门 " + deptId));
    }

    private int userOf(int resumeId) {
        return count("SELECT user_id FROM resume WHERE resume_id = ?", resumeId);
    }

    private int decisionOf(int resultId) {
        return count("SELECT decision FROM interview_result WHERE result_id = ?", resultId);
    }

    private int count(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    private int insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().intValue();
    }
}
