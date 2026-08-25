package club.boyuan.official.domain.profile.controller;

import club.boyuan.official.domain.profile.IProfileService;
import club.boyuan.official.domain.profile.dto.CandidateProfileDetail;
import club.boyuan.official.persistence.entity.AwardExperience;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 候选档案详情接口的权限边界。
 * <p>
 * 用最小 Spring 上下文只装配控制器与方法级鉴权，不打数据库（权限注解是否生效与 DB 无关）。
 * 守两条现状契约（ADR-0002）：
 * - resume:view / evaluation:view 可读完整档案（含 PII/简历/面试）；
 * - 纯 interview:evaluate（面试官）可读但被裁剪——只保留 userId/name/username/awards/submissions，
 *   不返回 email/phone/github/简历/面试安排（保住"面试官不读个人敏感信息"的边界）。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProfileApiSecurityTest.TestConfig.class)
class ProfileApiSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        IProfileService profileService() {
            return mock(IProfileService.class);
        }

        @Bean
        ProfileController profileController(IProfileService profileService) {
            return new ProfileController(profileService);
        }
    }

    @Autowired
    private ProfileController controller;

    @Autowired
    private IProfileService profileService;

    /** 造一个带 PII/简历/面试/获奖/成绩的完整档案，验证裁剪行为。 */
    private CandidateProfileDetail fullDetail() {
        CandidateProfileDetail d = new CandidateProfileDetail();
        d.setUserId(7);
        d.setName("张三");
        d.setUsername("zhangsan");
        d.setEmail("zhangsan@stu.ecnu.edu.cn");
        d.setPhone("13800000000");
        d.setGithub("https://github.com/zhangsan");
        d.setMajor("软件工程");
        d.setDeptName("技术部");
        d.setInterviews(List.of(new CandidateProfileDetail.InterviewScheduleSection()));
        d.setResumes(List.of(new club.boyuan.official.persistence.entity.Resume()));

        AwardExperience award = new AwardExperience();
        award.setAwardName("国家奖学金");
        d.setAwards(List.of(award));

        EvaluationSubmission sub = new EvaluationSubmission();
        sub.setTotalScore(320);
        d.setSubmissions(List.of(sub));
        return d;
    }

    @Test
    @WithMockUser(authorities = "resume:view")
    void resumeView读完整档案PII与简历面试都在() {
        when(profileService.getProfile(7)).thenReturn(fullDetail());
        var resp = assertDoesNotThrow(() -> controller.detail(7));
        CandidateProfileDetail body = resp.getBody().getData();
        assertEquals("zhangsan@stu.ecnu.edu.cn", body.getEmail());
        assertEquals(1, body.getResumes().size());
        assertEquals(1, body.getInterviews().size());
    }

    @Test
    @WithMockUser(authorities = "interview:evaluate")
    void 面试官可读但敏感信息被裁剪仅保留获奖与成绩() {
        when(profileService.getProfile(7)).thenReturn(fullDetail());
        var resp = assertDoesNotThrow(() -> controller.detail(7));
        CandidateProfileDetail body = resp.getBody().getData();
        // 裁剪 PII
        assertNull(body.getEmail());
        assertNull(body.getPhone());
        assertNull(body.getGithub());
        assertNull(body.getMajor());
        // 裁剪简历/面试
        assertEquals(0, body.getResumes().size());
        assertEquals(0, body.getInterviews().size());
        // 保留身份 + 获奖 + 成绩
        assertEquals("张三", body.getName());
        assertEquals(1, body.getAwards().size());
        assertEquals(1, body.getSubmissions().size());
    }

    @Test
    @WithMockUser(authorities = "evaluation:view")
    void evaluationView同样可读完整档案() {
        when(profileService.getProfile(7)).thenReturn(fullDetail());
        var resp = assertDoesNotThrow(() -> controller.detail(7));
        // evaluation:view 走完整视图，PII 不裁剪
        assertEquals("zhangsan@stu.ecnu.edu.cn", resp.getBody().getData().getEmail());
        assertEquals(1, resp.getBody().getData().getResumes().size());
    }

    @Test
    @WithMockUser(authorities = "resume:audit")
    void 无查看档案权限被拒() {
        assertThrows(AccessDeniedException.class, () -> controller.detail(7));
    }

    @Test
    void 未登录访问被拒() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.detail(7));
    }
}
