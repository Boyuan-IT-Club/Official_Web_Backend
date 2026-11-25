package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.RecruitmentQrCodeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "official.recruitment.qr-storage=CYCLE")
@AutoConfigureMockMvc
class CycleQrProjectionIntegrationTest {
    @Autowired private IRecruitmentQrCodeService codes;
    @Autowired private RecruitmentQrCodeMapper qrMapper;
    @Autowired private RecruitmentCycleMapper cycles;
    @Autowired private IRecruitmentCycleService cycleService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockBean private CosStorageService cos;
    private int cycleId;

    @BeforeEach
    void setup() throws Exception {
        RecruitmentCycle cycle = new RecruitmentCycle("projection-test", null, LocalDate.now(),
                LocalDate.now().plusDays(1), "fixture-" + System.nanoTime() % 100000000);
        cycleService.createRecruitmentCycle(cycle);
        cycleId = cycle.getCycleId();
        assertEquals("{}", jdbc.queryForObject("SELECT qr_codes FROM recruitment_cycle WHERE cycle_id=?", String.class, cycleId));
        when(cos.upload(any(), eq("qrcodes"))).thenAnswer(call ->
                ((MockMultipartFile) call.getArgument(0)).getOriginalFilename());
        when(cos.resolveAvatarUrl(anyString())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void cleanup() {
        cycles.deleteById(cycleId);
    }

    private RecruitmentQrCode upload(String type, int dept, String key) {
        return codes.upload(cycleId, type, dept, null, new MockMultipartFile("file", key, "image/png", new byte[]{1}));
    }

    @Test
    @WithMockUser(authorities = "cycle:manage")
    void regularCycleApiCannotReadOrOverwriteGroupCodes() throws Exception {
        upload("QA_GROUP", 0, "qa");
        var first = upload("DEPT", 1, "private-one");
        upload("DEPT", 2, "private-two");
        upload("MAIN_GROUP", 0, "main");
        assertEquals(List.of("private-one", "main"), codes.forAdmitted(cycleId, 1).stream()
                .map(RecruitmentQrCode::getImageUrl).toList());
        assertEquals("qa", codes.qaGroup(cycleId).getImageUrl());
        assertEquals(first.publicId(), upload("DEPT", 1, "replacement").publicId());

        mvc.perform(get("/api/cycles/{id}", cycleId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrCodes").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-two"))));
        mvc.perform(put("/api/cycles").contentType("application/json")
                .content("{\"cycleId\":" + cycleId + ",\"cycleName\":\"edited\",\"qrCodes\":{}}"))
                .andExpect(status().isOk());
        assertEquals("replacement", codes.forAdmitted(cycleId, 1).get(0).getImageUrl());
        codes.delete(first.publicId().toString());
        assertNull(qrMapper.findIdentifier(first.publicId().toString()));
        assertEquals(List.of("main"), codes.forAdmitted(cycleId, 1).stream().map(RecruitmentQrCode::getImageUrl).toList());
        mvc.perform(delete("/api/recruitment/qrcodes").param("cycleId", String.valueOf(cycleId))
                .param("qrType", "DEPT").param("deptId", "2")).andExpect(status().isOk());
        assertEquals(List.of("main", "qa"), codes.listByCycle(cycleId).stream().map(RecruitmentQrCode::getImageUrl).toList());

    }

    @Test
    void studentSeesOnlyCodesAllowedByTheirPublishedResult() throws Exception {
        upload("QA_GROUP", 0, "applicant-qa");
        upload("DEPT", 1, "dept-one");
        upload("DEPT", 2, "other-dept");
        upload("MAIN_GROUP", 0, "main");
        upload("MAIN_GROUP_2", 0, "main-two");
        String name = "qr-student-" + cycleId;
        jdbc.update("INSERT INTO user(username,password,email,name) VALUES(?,'test',?,'fixture')",
                name, name + "@example.invalid");
        int userId = jdbc.queryForObject("SELECT user_id FROM user WHERE username=?", Integer.class, name);
        String myResult = "/api/interview/schedule/my-result";
        String cycle = String.valueOf(cycleId);
        try {
            jdbc.update("INSERT INTO resume(user_id,cycle_id,status) VALUES(?,?,2)", userId, cycleId);
            int resumeId = jdbc.queryForObject("SELECT resume_id FROM resume WHERE user_id=? AND cycle_id=?", Integer.class, userId, cycleId);
            mvc.perform(get("/api/cycles/{id}", cycleId).with(user(name))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.cycleId").value(cycleId)).andExpect(jsonPath("$.data.qrCodes").doesNotExist());
            mvc.perform(get("/api/recruitment/qrcodes/qa").param("cycleId", cycle).with(user(name))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.imageUrl").value("applicant-qa"));
            mvc.perform(get("/api/recruitment/qrcodes").param("cycleId", cycle).with(user(name))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/recruitment/qrcodes").param("cycleId", cycle).param("qrType", "QA_GROUP")
                    .with(user(name))).andExpect(status().isForbidden());
            mvc.perform(get(myResult).param("cycleId", cycle).with(user(name))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist());
            jdbc.update("INSERT INTO interview_result(resume_id,cycle_id,user_id,decision,assigned_dept_id) VALUES(?,?,?,2,1)",
                    resumeId, cycleId, userId);
            mvc.perform(get(myResult).param("cycleId", cycle).with(user(name))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.decision").value(2)).andExpect(jsonPath("$.data.qrCodes").doesNotExist());
            jdbc.update("UPDATE interview_result SET decision=1 WHERE resume_id=?", resumeId);
            mvc.perform(get(myResult).param("cycleId", cycle).with(user(name))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.qrCodes[*].imageUrl",
                            org.hamcrest.Matchers.contains("dept-one", "main", "main-two")));
        } finally {
            jdbc.update("DELETE FROM interview_result WHERE user_id=? AND cycle_id=?", userId, cycleId);
            jdbc.update("DELETE FROM resume WHERE user_id=? AND cycle_id=?", userId, cycleId);
            jdbc.update("DELETE FROM user WHERE user_id=?", userId);
        }
    }

    @Test
    @WithMockUser(authorities = "cycle:manage")
    void parentOnlyKeepsLegacyIdsAndDoesNotReuseDeletedKeys() throws Exception {
        jdbc.update("UPDATE recruitment_cycle SET qr_codes=JSON_OBJECT('QA_GROUP',JSON_OBJECT('id',177003,'image','old','createdAt',1,'updatedAt',1)) WHERE cycle_id=?",cycleId);
        var legacy = codes.qaGroup(cycleId);
        var changed = upload("QA_GROUP", 0, "updated");
        assertEquals(legacy.getId(), changed.publicId());
        var created = upload("DEPT", 1, "fresh");
        String identifier = (String) created.publicId();
        assertNotNull(java.util.UUID.fromString(identifier));
        assertEquals(identifier, upload("DEPT", 1, "replacement").publicId());
        mvc.perform(get("/api/recruitment/qrcodes").param("cycleId", String.valueOf(cycleId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(identifier))
                .andExpect(jsonPath("$.data[1].id").value(legacy.getId()));
        codes.delete(identifier);
        var recreated = upload("DEPT", 1, "new");
        codes.delete(identifier);
        assertEquals(recreated.publicId(), codes.listByCycle(cycleId).get(0).publicId());
        codes.delete(legacy.getId());
        assertNull(codes.qaGroup(cycleId));
    }

    @Test
    void concurrentDepartmentUpdatesKeepBothEntries() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<RecruitmentQrCode> one = () -> { start.await(); return upload("DEPT", 1, "one"); };
            Callable<RecruitmentQrCode> two = () -> { start.await(); return upload("DEPT", 2, "two"); };
            var first = pool.submit(one);
            var second = pool.submit(two);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertEquals(List.of("one", "two"), codes.listByCycle(cycleId).stream()
                    .map(RecruitmentQrCode::getImageUrl).toList());
        } finally {
            pool.shutdownNow();
        }
    }
}
