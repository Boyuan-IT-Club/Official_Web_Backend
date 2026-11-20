package club.boyuan.official.persistence;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.controller.InterviewScheduleController;
import club.boyuan.official.domain.interview.dto.BatchDecisionRequestDTO;
import club.boyuan.official.domain.interview.dto.BatchDecisionResponseDTO;
import club.boyuan.official.domain.interview.dto.InterviewResultSaveDTO;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「已定即锁」：结果定了（通过 / 未通过）以后，批量操作不能再覆盖，只能逐个「更正」并填原因，
 * 每次变动都留痕。
 * <p>
 * 背景：管理员反馈录取完毕后还能把人标成未通过、定了的结果能被随意改，且改了什么、为什么改都无从查起。
 * 学生端实时读库，结果一改学生刷新就看到，而录取信可能早已发出。
 */
@SpringBootTest
class ResultDecidedLockIntegrationTest {

    /** V6 种子：部门 1 = 技术部、2 = 综合部 */
    private static final int DEPT_TECH = 1;
    private static final int DEPT_GENERAL = 2;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IInterviewResultService resultService;
    @Autowired
    private InterviewScheduleController scheduleController;

    private Integer cycleId;
    private Integer disabledDeptId;
    private final List<Integer> userIds = new ArrayList<>();
    private final List<String> usernames = new ArrayList<>();

    @Test
    @DisplayName("待定的人可以直接录入；已定的人再改必须填原因，改动写留痕并记下当时是否已通知")
    void decidedResultNeedsReasonToCorrect() {
        setUpCycle();
        int pending = result("PEND", 0, null, false);

        // 待定 → 通过：首次定结果，不需要原因
        resultService.update(pending, save(1, DEPT_TECH, null));
        assertEquals(1, decisionOf(pending));
        assertEquals(1, count("SELECT COUNT(*) FROM interview_result_change WHERE result_id = ? AND action = 'DECIDE'", pending));

        // 已定且已通知：不填原因不让改
        jdbc.update("UPDATE interview_result SET notified_at = NOW() WHERE result_id = ?", pending);
        BusinessException noReason = assertThrows(BusinessException.class,
                () -> resultService.update(pending, save(2, null, "  ")));
        assertEquals(BusinessExceptionEnum.INTERVIEW_RESULT_DECIDED.getCode(), noReason.getCode());
        assertEquals(1, decisionOf(pending), "被拒绝的更正不能留下半截改动");

        // 填了原因就能更正：未通过时清空部门，留痕记下改前改后与「改时已通知」
        resultService.update(pending, save(2, DEPT_TECH, "复核发现面试记录记错了人"));
        assertEquals(2, decisionOf(pending));
        assertNull(jdbc.queryForObject("SELECT assigned_dept_id FROM interview_result WHERE result_id = ?",
                Integer.class, pending), "未通过一律清空录取部门");
        Map<String, Object> change = jdbc.queryForMap("SELECT * FROM interview_result_change"
                + " WHERE result_id = ? AND action = 'CORRECT'", pending);
        assertEquals(1, ((Number) change.get("from_decision")).intValue());
        assertEquals(DEPT_TECH, ((Number) change.get("from_dept_id")).intValue());
        assertEquals(2, ((Number) change.get("to_decision")).intValue());
        assertNull(change.get("to_dept_id"));
        assertEquals(1, ((Number) change.get("was_notified")).intValue(), "已发过通知的更正要能被识别出来补发");
        assertEquals("复核发现面试记录记错了人", change.get("reason"));
    }

    @Test
    @DisplayName("内容没变不留痕；录取须选启用中的部门；预录取名单里的人不能在这里改；结果不存在报 404")
    void updateGuards() {
        setUpCycle();
        int admitted = result("ADM", 1, DEPT_TECH, false);

        // 原样提交：不报错、不写库、不留痕
        resultService.update(admitted, save(1, DEPT_TECH, null));
        assertEquals(0, count("SELECT COUNT(*) FROM interview_result_change WHERE result_id = ?", admitted));

        int pending = result("PEND", 0, null, false);
        BusinessException noDept = assertThrows(BusinessException.class,
                () -> resultService.update(pending, save(1, null, null)));
        assertEquals(BusinessExceptionEnum.MISSING_REQUIRED_FIELD.getCode(), noDept.getCode());
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> resultService.update(pending, save(1, disabledDeptId, null)));
        assertEquals(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND.getCode(), disabled.getCode());

        int drafted = result("DRF", 0, null, false);
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?)",
                cycleId, drafted, DEPT_TECH);
        BusinessException inDraft = assertThrows(BusinessException.class,
                () -> resultService.update(drafted, save(2, null, null)));
        assertEquals(BusinessExceptionEnum.INTERVIEW_RESULT_IN_PRE_ADMISSION.getCode(), inDraft.getCode());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> resultService.update(Integer.MAX_VALUE, save(2, null, null)));
        assertEquals(BusinessExceptionEnum.INTERVIEW_RESULT_NOT_FOUND.getCode(), missing.getCode());
    }

    @Test
    @DisplayName("批量录取只动未决定的人，已定与预录取名单里的分别计数；录取部门必须启用")
    void batchAdmitSkipsDecidedAndDrafted() {
        setUpCycle();
        int pending = result("P", 0, null, false);
        int adjusting = result("ADJ", 3, null, false);   // 待调剂：还没决定，可以批量处理
        int rejected = result("REJ", 2, null, true);
        int drafted = result("DRF", 0, null, false);
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?)",
                cycleId, drafted, DEPT_TECH);

        BatchDecisionRequestDTO req = new BatchDecisionRequestDTO();
        req.setCycleId(cycleId);
        req.setDecision(1);
        req.setAssignedDeptId(DEPT_GENERAL);
        req.setResultIds(List.of(pending, adjusting, rejected, drafted));
        BatchDecisionResponseDTO res = resultService.batchDecision(req);

        assertEquals(2, res.getUpdated());
        assertEquals(1, res.getSkippedDecided());
        assertEquals(1, res.getSkippedInPreAdmission());
        assertEquals(2, decisionOf(rejected), "已婉拒且已通知的人不能被一次批量录取翻过来");
        assertEquals(0, decisionOf(drafted));
        assertEquals(2, count("SELECT COUNT(*) FROM interview_result_change WHERE cycle_id = ? AND action = 'BATCH'", cycleId));

        req.setAssignedDeptId(disabledDeptId);
        req.setResultIds(List.of(result("P2", 0, null, false)));
        BusinessException disabled = assertThrows(BusinessException.class, () -> resultService.batchDecision(req));
        assertEquals(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND.getCode(), disabled.getCode());
    }

    @Test
    @DisplayName("学生端：未通过的人即使库里残留了部门，也不返回部门")
    void myResultHidesDeptForRejected() {
        setUpCycle();
        int rejected = result("ME", 2, DEPT_TECH, true);   // 历史数据：标未通过时没清部门
        String me = usernames.get(usernames.size() - 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me, null, List.of()));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) scheduleController.getMyResult(cycleId).getBody().getData();
            assertNotNull(data, "未通过是正式结果，要能看到");
            assertEquals(2, data.get("decision"));
            assertFalse(data.containsKey("assignedDeptId"), "未通过的人拿到部门会以为自己被某个部门录了");
            assertFalse(data.containsKey("assignedDeptName"));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(DEPT_TECH, count("SELECT assigned_dept_id FROM interview_result WHERE result_id = ?", rejected),
                "这里只改展示，不动历史数据（数据清理另走迁移）");
    }

    @AfterEach
    void cleanup() {
        if (cycleId != null) {
            jdbc.update("DELETE FROM interview_result_change WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM pre_admission_draft WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM interview_result WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM resume WHERE cycle_id = ?", cycleId);
        }
        userIds.forEach(id -> jdbc.update("DELETE FROM user WHERE user_id = ?", id));
        if (cycleId != null) {
            jdbc.update("DELETE FROM recruitment_cycle WHERE cycle_id = ?", cycleId);
        }
        if (disabledDeptId != null) {
            jdbc.update("DELETE FROM department WHERE dept_id = ?", disabledDeptId);
        }
    }

    // ── 测试数据 ──

    private void setUpCycle() {
        String tag = "lock" + System.nanoTime();
        cycleId = insert("INSERT INTO recruitment_cycle (cycle_name, start_date, end_date, academic_year, status, is_active)"
                        + " VALUES (?, ?, ?, '2099-2100', 2, 1)",
                tag, LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
        disabledDeptId = insert("INSERT INTO department (dept_name, dept_code, status) VALUES (?, ?, 0)",
                "停用部门" + tag, "OFF" + tag);
    }

    private int result(String who, int decision, Integer deptId, boolean notified) {
        String username = "lk" + System.nanoTime() + who;
        int userId = insert("INSERT INTO user (username, password, email, name) VALUES (?, 'x', ?, ?)",
                username, username + "@stu.ecnu.edu.cn", "候选人" + who);
        userIds.add(userId);
        usernames.add(username);
        int resumeId = insert("INSERT INTO resume (user_id, cycle_id, status) VALUES (?, ?, 2)", userId, cycleId);
        int resultId = insert("INSERT INTO interview_result (resume_id, cycle_id, user_id, decision, assigned_dept_id)"
                + " VALUES (?, ?, ?, ?, ?)", resumeId, cycleId, userId, decision, deptId);
        if (notified) {
            jdbc.update("UPDATE interview_result SET notified_at = NOW() WHERE result_id = ?", resultId);
        }
        return resultId;
    }

    private static InterviewResultSaveDTO save(int decision, Integer deptId, String reason) {
        InterviewResultSaveDTO dto = new InterviewResultSaveDTO();
        dto.setDecision(decision);
        dto.setAssignedDeptId(deptId);
        dto.setReason(reason);
        return dto;
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
