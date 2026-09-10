package club.boyuan.official.domain.resume.controller;

import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.service.IResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 简历初筛权限边界（resume-eval 评审闸门）。
 * <p>
 * 最小 Spring 上下文只装配 ResumeController 与方法级鉴权，不打数据库，
 * 验证两条权限契约：
 * - by-resume（按简历号取权威归属）：resume:view / resume:audit 可读；
 * - 状态 6（AI初筛中）写权限拆出 evaluation:run：resume:audit 无 6、evaluation:run 可写 6。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ResumeEvaluationSecurityTest.TestConfig.class)
class ResumeEvaluationSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        IResumeService resumeService() {
            return mock(IResumeService.class);
        }

        @Bean
        ResumeController resumeController(IResumeService resumeService) {
            return new ResumeController(
                    resumeService,
                    null, null, null, null, null, null);
        }
    }

    @Autowired
    private ResumeController controller;

    @Autowired
    private IResumeService resumeService;

    private ResumeDTO dto;

    @BeforeEach
    void setUp() {
        dto = new ResumeDTO();
        dto.setResumeId(42);
        dto.setUserId(7);
        dto.setCycleId(2026);
    }

    // ── by-resume 端点（方案 A 权威取数入口） ───────────────

    @Test
    @WithMockUser(authorities = "resume:view")
    void resumeView可读byResume权威归属() {
        when(resumeService.getResumeWithFieldValuesById(42)).thenReturn(dto);
        var resp = assertDoesNotThrow(() -> controller.getResumeByResumeId(42));
        assertEquals(dto, resp.getBody().getData());
    }

    @Test
    @WithMockUser(authorities = "resume:audit")
    void resumeAudit可读byResume权威归属() {
        when(resumeService.getResumeWithFieldValuesById(42)).thenReturn(dto);
        assertDoesNotThrow(() -> controller.getResumeByResumeId(42));
    }

    @Test
    @WithMockUser(authorities = "interview:evaluate")
    void 面试官无resumeView被拒() {
        assertThrows(AccessDeniedException.class, () -> controller.getResumeByResumeId(42));
    }

    @Test
    void 未登录访问byResume被拒() {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> controller.getResumeByResumeId(42));
    }

    // ── 状态 6 写权限收口 ─────────────────────────────────

    @Test
    @WithMockUser(authorities = "resume:audit")
    void resumeAudit不可写状态6() {
        org.junit.jupiter.api.Assertions.assertThrows(
                club.boyuan.official.common.exception.BusinessException.class,
                () -> controller.updateResumeStatus(42, 6));
    }

    @Test
    @WithMockUser(authorities = {"resume:audit", "evaluation:run"})
    void 持有evaluationRun可写状态6() {
        when(resumeService.getResumeById(42)).thenReturn(
                new club.boyuan.official.persistence.entity.Resume());
        assertDoesNotThrow(() -> controller.updateResumeStatus(42, 6));
    }

    @Test
    @WithMockUser(authorities = "resume:audit")
    void resumeAudit可写状态2() {
        when(resumeService.getResumeById(42)).thenReturn(
                new club.boyuan.official.persistence.entity.Resume());
        assertDoesNotThrow(() -> controller.updateResumeStatus(42, 2));
    }
}