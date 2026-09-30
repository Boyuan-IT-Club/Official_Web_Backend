package club.boyuan.official.domain.resume;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.controller.ResumeAttachmentController;
import club.boyuan.official.domain.resume.service.AttachmentAccess;
import club.boyuan.official.domain.resume.service.IResumeAttachmentService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeAttachment;
import club.boyuan.official.persistence.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件的访问控制。
 *
 * 以前列表与下载只要求「已登录」，附件 id、简历 id 又是连续整数，
 * 任何一个登录的学生从 1 往上数就能下载所有人的作品集、成绩单。
 */
class AttachmentAccessTest {

    private IResumeAttachmentService attachmentService;
    private IUserService userService;
    private IResumeService resumeService;
    private ResumeAttachmentController controller;

    private static final int OWNER = 10;
    private static final int OTHER_STUDENT = 11;
    private static final int REVIEWER = 2;

    @BeforeEach
    void setUp() {
        attachmentService = mock(IResumeAttachmentService.class);
        userService = mock(IUserService.class);
        resumeService = mock(IResumeService.class);
        controller = new ResumeAttachmentController(attachmentService, userService, resumeService);

        ResumeAttachment a = new ResumeAttachment();
        a.setId(121);
        a.setResumeId(158);
        a.setUserId(OWNER);
        a.setFileName("作品集.pdf");
        a.setContentType("application/pdf");
        when(attachmentService.getOrThrow(121)).thenReturn(a);
        when(attachmentService.presignedUrl(any(), anyBoolean())).thenReturn("https://cos.example/signed");

        Resume r = new Resume();
        r.setResumeId(158);
        r.setUserId(OWNER);
        when(resumeService.getResumeById(158)).thenReturn(r);
        when(attachmentService.listByResume(158)).thenReturn(List.of());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(int userId, String... authorities) {
        User u = new User();
        u.setUserId(userId);
        u.setUsername("u" + userId);
        when(userService.getUserByUsername("u" + userId)).thenReturn(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u" + userId, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
    }

    // ---------- 规则本身 ----------

    @Test
    @DisplayName("本人能看自己的附件")
    void ownerCanView() {
        assertTrue(AttachmentAccess.canView(OWNER, OWNER, List.of()));
    }

    @Test
    @DisplayName("别的学生看不了 —— 这就是原来那个洞")
    void otherStudentCannotView() {
        assertFalse(AttachmentAccess.canView(OWNER, OTHER_STUDENT, List.of()));
    }

    @Test
    @DisplayName("看候选人材料的四类权限各自都放行")
    void viewerAuthoritiesCanView() {
        for (String auth : List.of("resume:view", "resume:audit", "interview:result", "interview:board:manage")) {
            assertTrue(AttachmentAccess.canView(OWNER, REVIEWER, List.of(auth)), auth);
        }
    }

    @Test
    @DisplayName("只有面试评价权限（interview:evaluate）不算 —— 不能借此翻全库附件")
    void evaluateAloneIsNotEnough() {
        assertFalse(AttachmentAccess.canView(OWNER, REVIEWER, List.of("interview:evaluate")));
    }

    @Test
    @DisplayName("超管兜底放行")
    void superAdminCanView() {
        assertTrue(AttachmentAccess.canView(OWNER, REVIEWER, List.of("ROLE_SUPER_ADMIN")));
    }

    // ---------- 三个接口都要守住 ----------

    @Test
    @DisplayName("取直链：别的学生 403，且根本不去签名")
    void urlDeniedForOtherStudent() {
        loginAs(OTHER_STUDENT);
        BusinessException e = assertThrows(BusinessException.class, () -> controller.url(121, true));
        assertEquals(BusinessExceptionEnum.PERMISSION_DENIED.getCode(), e.getCode());
        verify(attachmentService, never()).presignedUrl(any(), anyBoolean());
    }

    @Test
    @DisplayName("取直链：本人拿得到")
    void urlAllowedForOwner() {
        loginAs(OWNER);
        var res = controller.url(121, true);
        assertEquals("https://cos.example/signed", res.getBody().getData().get("url"));
    }

    @Test
    @DisplayName("取直链：审核人拿得到")
    void urlAllowedForReviewer() {
        loginAs(REVIEWER, "resume:view");
        assertEquals("https://cos.example/signed", controller.url(121, true).getBody().getData().get("url"));
    }

    @Test
    @DisplayName("COS 未启用时 url 为 null，前端据此退回流式接口")
    void urlNullWhenCosDisabled() {
        loginAs(OWNER);
        when(attachmentService.presignedUrl(any(), anyBoolean())).thenReturn(null);
        assertNull(controller.url(121, true).getBody().getData().get("url"));
    }

    @Test
    @DisplayName("流式下载 /content：别的学生同样 403，且不去开 COS 对象")
    void contentDeniedForOtherStudent() {
        loginAs(OTHER_STUDENT);
        assertThrows(BusinessException.class, () -> controller.content(121, false, new MockHttpServletResponse()));
        verify(attachmentService, never()).open(any());
    }

    @Test
    @DisplayName("附件列表：别的学生 403 —— 文件名与大小也是个人信息")
    void listDeniedForOtherStudent() {
        loginAs(OTHER_STUDENT);
        assertThrows(BusinessException.class, () -> controller.list(158));
    }

    @Test
    @DisplayName("附件列表：本人与审核人都能看")
    void listAllowedForOwnerAndReviewer() {
        loginAs(OWNER);
        controller.list(158);
        loginAs(REVIEWER, "resume:audit");
        controller.list(158);
    }

    @Test
    @DisplayName("列不存在的简历：学生一律拒绝，不让「存在 / 不存在」成为可枚举的信号")
    void listOfMissingResumeDeniedForStudent() {
        loginAs(OTHER_STUDENT);
        when(resumeService.getResumeById(999)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.list(999));
    }
}
