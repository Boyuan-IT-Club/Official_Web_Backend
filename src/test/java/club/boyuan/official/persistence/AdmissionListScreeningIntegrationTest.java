package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.dto.BatchDecisionRequestDTO;
import club.boyuan.official.domain.interview.dto.BatchDecisionResponseDTO;
import club.boyuan.official.domain.interview.dto.InterviewResultResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionBatchRequestDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionListResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionMutationResponseDTO;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import club.boyuan.official.domain.interview.service.PreAdmissionService;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.PreAdmissionDraft;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 录取结果与预录取名单：不含初筛未通过的人，线上面试排在最后。
 * <p>
 * 背景：「生成结果名单」按「已提交（status ≥ 2）」取简历，初筛未通过（status 5）也在其中，
 * 于是一批没面试过的人出现在「结果与通知」和预录取终审里；被批量婉拒时还会再收到一封未录取邮件。
 * 另外线上面试（零散单独约）和线下（按教室成批面）混排，对照起来很乱。
 * <p>
 * 这些规则主要落在 SQL 里（JSON 函数、EXISTS、分页计数、多表 UPDATE 里的子查询），
 * 只有对着真库跑才算数。测试数据用 JdbcTemplate 直接插：User 实体的通用插入会撞缺失列。
 */
@SpringBootTest
class AdmissionListScreeningIntegrationTest {

    /** V6 种子：部门 1 = 技术部 */
    private static final int DEPT_TECH = 1;
    private static final int RESUME_SUBMITTED = 2;
    private static final int RESUME_SCREEN_REJECTED = 5;
    private static final int DECISION_PENDING = 0;
    private static final int DECISION_ADMITTED = 1;
    private static final int DECISION_REJECTED = 2;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IInterviewResultService resultService;
    @Autowired
    private PreAdmissionService preAdmissionService;

    private Integer cycleId;
    private Integer fieldId;
    private final List<Integer> userIds = new ArrayList<>();

    @Test
    @DisplayName("生成跳过初筛未通过；列表隐藏其待定行、保留已决定行；线上排最后；预录取写入/名单/人数/转正口径一致")
    void admissionListsExcludeScreenRejectedAndPutOnlineLast() {
        String tag = "adm" + System.nanoTime();
        cycleId = insert("INSERT INTO recruitment_cycle (cycle_name, start_date, end_date, academic_year, status, is_active)"
                        + " VALUES (?, ?, ?, '2099-2100', 2, 1)",
                tag, LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
        fieldId = insert("INSERT INTO resume_field_definition (cycle_id, field_key, field_label)"
                + " VALUES (?, 'expected_interview_time', '面试时间')", cycleId);

        // A 线下已排   B 线上已排（改期转线上）   C 没排、选了不能线下   D 没排、能线下（人数原因落选）
        // G 线下已排、面试意向是坏 JSON（不能让整条查询报错）
        int a = resume(tag, "A", RESUME_SUBMITTED);
        int b = resume(tag, "B", RESUME_SUBMITTED);
        int c = resume(tag, "C", RESUME_SUBMITTED);
        int d = resume(tag, "D", RESUME_SUBMITTED);
        int g = resume(tag, "G", RESUME_SUBMITTED);
        // E 初筛未通过、修复前已被生成过一行待定结果   F 初筛未通过、但已有决定（婉拒）
        // H 初筛未通过、还没有结果行（生成时必须跳过）
        int e = resume(tag, "E", RESUME_SCREEN_REJECTED);
        int f = resume(tag, "F", RESUME_SCREEN_REJECTED);
        int h = resume(tag, "H", RESUME_SCREEN_REJECTED);

        schedule(a, 0);
        schedule(b, 1);
        schedule(g, 0);
        attendance(c, "{\"first\":\"\",\"second\":\"\",\"canAttend\":\"no\",\"customTime\":\"在外地\"}");
        attendance(d, "{\"first\":\"周六上午\",\"second\":\"\",\"canAttend\":\"yes\"}");
        attendance(g, "not-json{");

        int resultE = legacyResult(e, DECISION_PENDING);
        int resultF = legacyResult(f, DECISION_REJECTED);

        // ── 生成 ──
        int created = resultService.seedFromSchedules(cycleId);
        assertEquals(5, created, "应只为 A B C D G 生成结果行");
        assertEquals(0, count("SELECT COUNT(*) FROM interview_result WHERE resume_id = ?", h),
                "初筛未通过的 H 不该被生成进结果名单");

        // ── 结果列表 ──
        InterviewResultResponseDTO page = resultService.list(cycleId, null, null, null, 1, 50);
        List<InterviewResult> rows = page.getInterviewResults();
        Map<Integer, InterviewResult> byResume = rows.stream()
                .collect(Collectors.toMap(InterviewResult::getResumeId, r -> r));

        assertFalse(byResume.containsKey(e), "初筛未通过且仍待定的 E 不该出现在结果列表里");
        assertTrue(byResume.containsKey(f), "初筛未通过但已有决定的 F 必须照常显示，不能藏起一个真实结果");
        assertEquals(6, rows.size(), "可见 6 行：A B C D G F");
        assertEquals(6L, page.getTotal(), "分页总数要和过滤后的行数一致");

        assertEquals(Boolean.TRUE, byResume.get(b).getOnlineInterview(), "线上已排的 B 算线上");
        assertEquals(Boolean.TRUE, byResume.get(c).getOnlineInterview(), "没排、选了不能线下的 C 算线上");
        assertEquals(Boolean.FALSE, byResume.get(a).getOnlineInterview(), "线下已排的 A 不算线上");
        assertEquals(Boolean.FALSE, byResume.get(d).getOnlineInterview(), "没排但能线下的 D 不算线上");
        assertEquals(Boolean.FALSE, byResume.get(g).getOnlineInterview(), "坏 JSON 不报错，按线下处理");

        int firstOnline = indexOfFirst(rows, true);
        int lastOffline = indexOfLast(rows, false);
        assertTrue(lastOffline < firstOnline,
                "线上面试必须整体排在线下之后，实际顺序：" + rows.stream()
                        .map(r -> r.getResumeId() + (Boolean.TRUE.equals(r.getOnlineInterview()) ? "(线上)" : ""))
                        .collect(Collectors.joining(", ")));

        // ── 预录取 ──
        int resultA = byResume.get(a).getResultId();
        PreAdmissionBatchRequestDTO req = new PreAdmissionBatchRequestDTO();
        req.setCycleId(cycleId);
        req.setAssignedDeptId(DEPT_TECH);
        req.setResultIds(List.of(resultA, resultE));
        PreAdmissionMutationResponseDTO saved = preAdmissionService.save(req);
        assertEquals(1, saved.getAffected(), "只有 A 能进草稿");
        assertEquals(List.of(resultE), saved.getSkipped(), "初筛未通过的 E 应被拒收");

        // 模拟修复前就被加进草稿的 E
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?)",
                cycleId, resultE, DEPT_TECH);

        PreAdmissionListResponseDTO drafts = preAdmissionService.list(cycleId, null, null, 1, 50);
        assertEquals(List.of(resultA),
                drafts.getCandidates().stream().map(PreAdmissionDraft::getResultId).toList(),
                "预录取名单里只该有 A");
        assertEquals(1L, drafts.getTotal());
        assertEquals(1, ((Number) drafts.getDepartmentStats().get(0).get("candidateCount")).intValue(),
                "部门人数要和名单里看得到的人对得上");

        int published = preAdmissionService.finalizeCycle(cycleId);
        assertEquals(1, published, "转正只发布看得到的 A，不能因为 E 而整批回滚");
        assertEquals(DECISION_ADMITTED, count("SELECT decision FROM interview_result WHERE result_id = ?", resultA));
        assertEquals(DECISION_PENDING, count("SELECT decision FROM interview_result WHERE result_id = ?", resultE),
                "初筛未通过的 E 绝不能被悄悄录取");
    }

    @Test
    @DisplayName("批量标记未通过 onlyPending：只改执行时仍待定的行；已录取/初筛未通过/预录取名单里/别届的一律跳过，转正不受影响")
    void batchRejectOnlyPendingNeverOverwritesDecidedRows() {
        String tag = "rej" + System.nanoTime();
        cycleId = insert("INSERT INTO recruitment_cycle (cycle_name, start_date, end_date, academic_year, status, is_active)"
                        + " VALUES (?, ?, ?, '2099-2100', 2, 1)",
                tag, LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));

        int p1 = legacyResult(resume(tag, "P1", RESUME_SUBMITTED), DECISION_PENDING);
        int p2 = legacyResult(resume(tag, "P2", RESUME_SUBMITTED), DECISION_PENDING);
        // 前端列表加载之后，别的管理员把这个人录取了（比如预录取转正）
        int admitted = legacyResult(resume(tag, "ADM", RESUME_SUBMITTED), DECISION_ADMITTED);
        jdbc.update("UPDATE interview_result SET assigned_dept_id = ? WHERE result_id = ?", DEPT_TECH, admitted);
        // 初筛未通过、仍待定：有单独的通知入口，这里不能顺手改成面试未通过
        int screenRejected = legacyResult(resume(tag, "SR", RESUME_SCREEN_REJECTED), DECISION_PENDING);
        // 已在预录取名单里（转正前 decision 仍是 0）：部门打算录取，不能被一键标掉
        int drafted = legacyResult(resume(tag, "DRF", RESUME_SUBMITTED), DECISION_PENDING);
        jdbc.update("INSERT INTO pre_admission_draft (cycle_id, result_id, assigned_dept_id) VALUES (?, ?, ?)",
                cycleId, drafted, DEPT_TECH);
        int foreign = Integer.MAX_VALUE;

        BatchDecisionRequestDTO req = new BatchDecisionRequestDTO();
        req.setCycleId(cycleId);
        req.setDecision(DECISION_REJECTED);
        req.setOnlyPending(true);
        req.setResultIds(List.of(p1, p2, admitted, screenRejected, drafted, foreign));
        BatchDecisionResponseDTO res = resultService.batchDecision(req);

        assertEquals(2, res.getUpdated(), "只有仍待定的 P1、P2 被改");
        assertEquals(java.util.Set.of(admitted, screenRejected, drafted, foreign), new java.util.HashSet<>(res.getSkipped()));
        assertEquals(DECISION_REJECTED, count("SELECT decision FROM interview_result WHERE result_id = ?", p1));
        assertEquals(DECISION_REJECTED, count("SELECT decision FROM interview_result WHERE result_id = ?", p2));
        assertEquals(DECISION_ADMITTED, count("SELECT decision FROM interview_result WHERE result_id = ?", admitted),
                "别人刚录取的人绝不能被一份旧列表改成未通过");
        assertEquals(DEPT_TECH, count("SELECT assigned_dept_id FROM interview_result WHERE result_id = ?", admitted),
                "录取部门也得原样保留");
        assertEquals(DECISION_PENDING, count("SELECT decision FROM interview_result WHERE result_id = ?", screenRejected));
        assertEquals(DECISION_PENDING, count("SELECT decision FROM interview_result WHERE result_id = ?", drafted),
                "预录取名单里的人不能被一键标成未通过");
        assertEquals(1, preAdmissionService.finalizeCycle(cycleId),
                "一键标记之后照常能转正，不会因为草稿成员被改掉而整批回滚");
        assertEquals(DECISION_ADMITTED, count("SELECT decision FROM interview_result WHERE result_id = ?", drafted));

        // 不开 onlyPending 的旧路径（手动勾选后批量标记）这次不改：仍按 ID 显式覆盖。
        // 「结果确定后不能再改」的锁定规则在录取流程梳理里另行设计，这里只锁住现状不被意外改坏
        BatchDecisionRequestDTO explicit = new BatchDecisionRequestDTO();
        explicit.setCycleId(cycleId);
        explicit.setDecision(DECISION_REJECTED);
        explicit.setResultIds(List.of(admitted));
        assertEquals(1, resultService.batchDecision(explicit).getUpdated());
        assertEquals(DECISION_REJECTED, count("SELECT decision FROM interview_result WHERE result_id = ?", admitted));
        assertEquals(0, count("SELECT COUNT(*) FROM interview_result WHERE result_id = ? AND assigned_dept_id IS NOT NULL",
                admitted), "标记未通过时清空录取部门");
    }

    @AfterEach
    void cleanup() {
        if (cycleId != null) {
            // 草稿随结果级联删；结果没有挂在简历上，要先手动删
            jdbc.update("DELETE FROM pre_admission_draft WHERE cycle_id = ?", cycleId);
            jdbc.update("DELETE FROM interview_result WHERE cycle_id = ?", cycleId);
            // 安排、字段值随简历级联删
            jdbc.update("DELETE FROM resume WHERE cycle_id = ?", cycleId);
        }
        if (fieldId != null) {
            jdbc.update("DELETE FROM resume_field_definition WHERE field_id = ?", fieldId);
        }
        userIds.forEach(id -> jdbc.update("DELETE FROM user WHERE user_id = ?", id));
        if (cycleId != null) {
            jdbc.update("DELETE FROM recruitment_cycle WHERE cycle_id = ?", cycleId);
        }
    }

    // ── 测试数据 ──

    private int resume(String tag, String who, int status) {
        int userId = insert("INSERT INTO user (username, password, email, name) VALUES (?, 'x', ?, ?)",
                tag + who, tag + who + "@stu.ecnu.edu.cn", "候选人" + who);
        userIds.add(userId);
        return insert("INSERT INTO resume (user_id, cycle_id, status) VALUES (?, ?, ?)", userId, cycleId, status);
    }

    private void schedule(int resumeId, int interviewMode) {
        Integer userId = jdbc.queryForObject("SELECT user_id FROM resume WHERE resume_id = ?", Integer.class, resumeId);
        jdbc.update("INSERT INTO interview_schedule (resume_id, user_id, cycle_id, status, interview_mode)"
                + " VALUES (?, ?, ?, 1, ?)", resumeId, userId, cycleId, interviewMode);
    }

    private void attendance(int resumeId, String json) {
        jdbc.update("INSERT INTO resume_field_value (resume_id, field_id, field_value) VALUES (?, ?, ?)",
                resumeId, fieldId, json);
    }

    /** 修复前就已存在的结果行（当时的「生成」没有排除初筛未通过） */
    private int legacyResult(int resumeId, int decision) {
        Integer userId = jdbc.queryForObject("SELECT user_id FROM resume WHERE resume_id = ?", Integer.class, resumeId);
        return insert("INSERT INTO interview_result (resume_id, cycle_id, user_id, decision) VALUES (?, ?, ?, ?)",
                resumeId, cycleId, userId, decision);
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

    private static int indexOfFirst(List<InterviewResult> rows, boolean online) {
        for (int i = 0; i < rows.size(); i++) {
            if (Boolean.valueOf(online).equals(rows.get(i).getOnlineInterview())) {
                return i;
            }
        }
        return rows.size();
    }

    private static int indexOfLast(List<InterviewResult> rows, boolean online) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (Boolean.valueOf(online).equals(rows.get(i).getOnlineInterview())) {
                return i;
            }
        }
        return -1;
    }
}
