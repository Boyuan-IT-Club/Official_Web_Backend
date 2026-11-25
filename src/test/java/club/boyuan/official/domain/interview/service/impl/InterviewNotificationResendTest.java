package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.utils.MessageUtils;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSlotService;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.domain.user.service.DepartmentService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.messaging.InterviewNotificationMessage;
import club.boyuan.official.messaging.InterviewNotificationProducer;
import club.boyuan.official.persistence.entity.InterviewNotificationLog;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewNotificationLogMapper;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Envelopes must be claimed before sending the prepared result mail. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewNotificationResendTest {

    @Mock private InterviewNotificationProducer notificationProducer;
    @Mock private club.boyuan.official.messaging.DeliveryLedger ledger;
    @Mock private IInterviewScheduleService interviewScheduleService;
    @Mock private IInterviewSlotService interviewSlotService;
    @Mock private IResumeService resumeService;
    @Mock private IUserService userService;
    @Mock private InterviewResultMapper interviewResultMapper;
    @Mock private DepartmentService departmentService;
    @Mock private ResumeDataService resumeDataService;
    @Mock private MessageUtils messageUtils;
    @Mock private RecruitmentCycleMapper recruitmentCycleMapper;
    @Mock private IRecruitmentQrCodeService qrCodeService;

    @InjectMocks
    private InterviewNotificationServiceImpl service;

    private static final int RESULT_ID = 42;

    @BeforeEach
    void setUp() {
        InterviewResult admitted = new InterviewResult()
                .setResultId(RESULT_ID).setUserId(7).setDecision(1)   // 1 = 录取
                .setScheduleId(null).setAssignedDeptId(null);          // 无场次、无部门：走最短的模板路径
        when(interviewResultMapper.selectById(RESULT_ID)).thenReturn(admitted);

        User u = new User();
        u.setUserId(7);
        u.setName("张三");
        u.setEmail("zhangsan@stu.ecnu.edu.cn");
        when(userService.getById(7)).thenReturn(u);
        when(interviewScheduleService.getById(any())).thenReturn(null);

        var claims = new java.util.HashSet<String>();
        when(ledger.observe(any(), org.mockito.ArgumentMatchers.isNull())).thenAnswer(inv -> {
            InterviewNotificationMessage m = inv.getArgument(0);
            return m.getDeliveryId() == null ? null : m;
        });
        when(ledger.claim(any(), anyString(), anyString(), any())).thenAnswer(inv ->
                claims.add(((InterviewNotificationMessage) inv.getArgument(0)).getRequestId()));
    }

    private static InterviewNotificationMessage templated(String requestId) {
        var m = new InterviewNotificationMessage(club.boyuan.official.infra.notification.InterviewNotificationType.ADMISSION,
                null, RESULT_ID, requestId, null);
        m.setDeliveryId(4L);
        return m;
    }

    @Test
    @DisplayName("管理员第二次发送（新 requestId）必须真的再发一封")
    void resendWithNewRequestIdDeliversAgain() {
        service.deliver(templated("req-1"));
        service.deliver(templated("req-2"));   // 第二次点「发送通知」

        // 老代码按 (type, resultId) 去重，第二封会被静默吞掉——界面成功、邮箱没有
        verify(messageUtils, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("MQ 重投同一条消息（同 requestId）只发一封")
    void redeliveryOfSameMessageIsDeduplicated() {
        InterviewNotificationMessage m = templated("req-same");
        service.deliver(m);
        service.deliver(m);   // acknowledge-mode: auto 下 SMTP 超时就会这样重投

        verify(messageUtils, times(1)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unversionedMessageCannotCallSmtp() {
        var m = templated("old");
        m.setDeliveryId(null);
        service.deliver(m);
        verify(messageUtils, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("无面试安排的结果（V34），录取邮件也要带上周期配的入群二维码")
    void admissionWithoutScheduleStillCarriesQrCodes() {
        InterviewResult noSchedule = new InterviewResult()
                .setResultId(RESULT_ID).setUserId(7).setDecision(1)
                .setScheduleId(null).setCycleId(6).setAssignedDeptId(2).setResumeId(33);
        when(interviewResultMapper.selectById(RESULT_ID)).thenReturn(noSchedule);
        when(qrCodeService.forAdmitted(6, 2)).thenReturn(List.of(
                new club.boyuan.official.persistence.entity.RecruitmentQrCode()
                        .setQrType(club.boyuan.official.persistence.entity.RecruitmentQrCode.TYPE_DEPT)
                        .setDeptId(2).setRemark("综合部群")
                        .setImageUrl("https://static.boyuan.club/api/files/qrcodes/x.jpg")));

        service.deliver(templated("req-qr"));

        // 修复前：周期号只从面试安排上取，安排为空时二维码查询被整个跳过，
        // 这个 verify 会因为 forAdmitted 从未被调用而失败
        verify(qrCodeService).forAdmitted(6, 2);
        verify(messageUtils).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("无面试安排的结果，收件邮箱用简历里填的，不是注册邮箱")
    void admissionWithoutScheduleUsesResumeEmail() {
        InterviewResult noSchedule = new InterviewResult()
                .setResultId(RESULT_ID).setUserId(7).setDecision(1)
                .setScheduleId(null).setCycleId(6).setAssignedDeptId(2).setResumeId(33);
        when(interviewResultMapper.selectById(RESULT_ID)).thenReturn(noSchedule);
        club.boyuan.official.persistence.entity.Resume resume =
                new club.boyuan.official.persistence.entity.Resume();
        when(resumeService.getResumeById(33)).thenReturn(resume);
        when(resumeDataService.getResumeEmail(resume)).thenReturn("resume-mail@stu.ecnu.edu.cn");
        when(resumeDataService.getResumeName(resume)).thenReturn("李四");

        service.deliver(templated("req-mail"));

        verify(messageUtils).sendHtmlEmail(
                org.mockito.ArgumentMatchers.eq("resume-mail@stu.ecnu.edu.cn"),
                anyString(), anyString(), anyString());
    }

    @Test
    void unknownSmtpOutcomeIsSettledBeforeDuplicateDelivery() {
        var m = templated("uncertain");
        org.mockito.Mockito.doThrow(new IllegalStateException()).when(messageUtils)
                .sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
        service.deliver(m);
        service.deliver(m);
        verify(ledger).finish(org.mockito.ArgumentMatchers.eq(m),anyString(),org.mockito.ArgumentMatchers.eq(false),anyString());
        verify(messageUtils).sendHtmlEmail(anyString(),anyString(),anyString(),anyString());
    }

    @Test
    void pendingResultCannotUseCustomTemplate() {
        when(interviewResultMapper.selectById(RESULT_ID)).thenReturn(new InterviewResult().setDecision(0));
        var m = templated("draft");
        m.setCustomBody("hello");
        service.deliver(m);
        verify(messageUtils, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("补充说明真的进了正文，且模板原文仍在")
    void customBodyIsAppendedNotReplacing() {
        var message = templated("custom");
        message.setCustomBody("见面会");
        service.deliver(message);

        org.mockito.ArgumentCaptor<String> html = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> plain = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(messageUtils).sendHtmlEmail(anyString(), anyString(), html.capture(), plain.capture());

        // 模板原文与补充说明共存 —— 早先的实现会把整封信换成那一句
        org.junit.jupiter.api.Assertions.assertTrue(html.getValue().contains("恭喜"));
        org.junit.jupiter.api.Assertions.assertTrue(html.getValue().contains("见面会"));
        org.junit.jupiter.api.Assertions.assertTrue(plain.getValue().contains("恭喜"));
        org.junit.jupiter.api.Assertions.assertTrue(plain.getValue().contains("见面会"));
    }
}
