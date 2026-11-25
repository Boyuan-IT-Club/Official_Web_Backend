package club.boyuan.official.persistence;

import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "official.recruitment.qr-storage=CYCLE")
@AutoConfigureMockMvc
class CycleQrProjectionIntegrationTest {
    @Autowired private IRecruitmentQrCodeService codes;
    @Autowired private RecruitmentQrCodeMapper qrMapper;
    @Autowired private RecruitmentCycleMapper cycles;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockBean private CosStorageService cos;
    private int cycleId;

    @BeforeEach
    void setup() throws Exception {
        RecruitmentCycle cycle = new RecruitmentCycle("projection-test", null, LocalDate.now(),
                LocalDate.now().plusDays(1), "fixture-" + System.nanoTime() % 100000000);
        cycles.insert(cycle);
        cycleId = cycle.getCycleId();
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
        assertEquals(first.getId(), upload("DEPT", 1, "replacement").getId());

        mvc.perform(get("/api/cycles/{id}", cycleId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrCodes").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-two"))));
        mvc.perform(put("/api/cycles").contentType("application/json")
                .content("{\"cycleId\":" + cycleId + ",\"cycleName\":\"edited\",\"qrCodes\":{}}"))
                .andExpect(status().isOk());
        assertEquals("replacement", codes.forAdmitted(cycleId, 1).get(0).getImageUrl());
        codes.delete(first.getId());
        assertNull(qrMapper.selectById(first.getId()));
        assertEquals(List.of("main"), codes.forAdmitted(cycleId, 1).stream().map(RecruitmentQrCode::getImageUrl).toList());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_cycle c JOIN cycle_qr_source v "
                + "ON c.cycle_id=v.cycle_id WHERE c.cycle_id=? AND NOT(c.qr_codes <=> v.qr_codes)", Integer.class, cycleId));
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
