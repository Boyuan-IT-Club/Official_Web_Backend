package club.boyuan.official.domain.resume.controller;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import club.boyuan.official.domain.resume.service.IResumePhotoService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社员不投简历：建草稿 / 存字段 / 提交三条路都必须在服务端被挡住。
 *
 * 线上真实事故：前端对社员传了 autoCreate=false，但 userInfo 是异步到的，
 * 首轮 initData 跑的时候 isMember 还是 false，草稿在那一轮就建掉了。
 * 结果 11 个社员攒出 24 条空草稿，横跨 10 个周期。
 * 所以闸口必须在服务端——前端那层只是省一次请求，不是防线。
 */
class ResumeMemberGuardTest {

    private IResumeService resumeService;
    private IUserService userService;
    private ResumeController controller;

    private User member;
    private User applicant;

    @BeforeEach
    void setUp() {
        resumeService = mock(IResumeService.class);
        userService = mock(IUserService.class);

        controller = new ResumeController(
                resumeService,
                mock(IResumePhotoService.class),
                mock(IResumeFieldDefinitionService.class),
                userService,
                mock(club.boyuan.official.persistence.mapper.ResumeMapper.class),
                mock(club.boyuan.official.persistence.mapper.RecruitmentCycleMapper.class),
                mock(club.boyuan.official.domain.interview.service.InterviewNotificationService.class));

        member = new User();
        member.setUserId(2);
        member.setUsername("member");
        member.setIsMember(true);

        applicant = new User();
        applicant.setUserId(3);
        applicant.setUsername("applicant");
        applicant.setIsMember(false);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        when(userService.getUserByUsername(user.getUsername())).thenReturn(user);
    }

    @Test
    @DisplayName("社员打开投递页：不建草稿，返回空")
    void memberGetsNoDraft() {
        login(member);
        when(resumeService.getResumeWithFieldValues(2, 14)).thenReturn(null);

        var res = controller.getResumeByCycleId(14, true);

        assertNull(res.getBody().getData(), "社员没有简历时应返回 null");
        verify(resumeService, never()).createResume(any(Resume.class));
    }

    @Test
    @DisplayName("非社员打开投递页：照常自动建草稿")
    void applicantStillGetsDraft() {
        login(applicant);
        ResumeDTO created = new ResumeDTO();
        created.setStatus(1);
        when(resumeService.getResumeWithFieldValues(3, 14))
                .thenReturn(null)      // 第一次查：没有
                .thenReturn(created);  // 建完再查：有了

        controller.getResumeByCycleId(14, true);

        verify(resumeService).createResume(any(Resume.class));
    }

    @Test
    @DisplayName("社员已有历史简历时照常读得到——挡的是创建，不是查看")
    void memberCanStillReadExistingResume() {
        login(member);
        ResumeDTO existing = new ResumeDTO();
        existing.setStatus(2);
        when(resumeService.getResumeWithFieldValues(2, 2)).thenReturn(existing);

        var res = controller.getResumeByCycleId(2, true);

        assertEquals(existing, res.getBody().getData());
        verify(resumeService, never()).createResume(any(Resume.class));
    }

    @Test
    @DisplayName("社员存字段值：3013 拒绝，且不落库")
    void memberCannotSaveFieldValues() {
        login(member);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.saveFieldValues(14, List.of()));

        assertEquals(BusinessExceptionEnum.RESUME_MEMBER_NO_APPLY.getCode(), e.getCode());
        verify(resumeService, never()).createResume(any(Resume.class));
        verify(resumeService, never()).saveFieldValues(any());
    }

    @Test
    @DisplayName("社员提交简历：3013 拒绝")
    void memberCannotSubmit() {
        login(member);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.submitResume(14));

        assertEquals(BusinessExceptionEnum.RESUME_MEMBER_NO_APPLY.getCode(), e.getCode());
    }

    @Test
    @DisplayName("社员更新简历：3013 拒绝")
    void memberCannotUpdate() {
        login(member);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.updateResume(14, List.of()));

        assertEquals(BusinessExceptionEnum.RESUME_MEMBER_NO_APPLY.getCode(), e.getCode());
        verify(resumeService, never()).saveFieldValues(any());
    }

    @Test
    @DisplayName("周期闸口先于社员闸口：两道闸都在，顺序不影响结论")
    void cycleGuardStillApplies() {
        login(member);
        org.mockito.Mockito.doThrow(new BusinessException(BusinessExceptionEnum.RESUME_CYCLE_CLOSED))
                .when(resumeService).assertCycleOpen(anyInt());

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.saveFieldValues(99, List.of()));

        assertEquals(BusinessExceptionEnum.RESUME_CYCLE_CLOSED.getCode(), e.getCode());
    }
}
