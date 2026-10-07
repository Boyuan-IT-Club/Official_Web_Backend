package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.utils.MessageUtils;
import club.boyuan.official.domain.interview.dto.InterviewBookingDTO;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSlotService;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.domain.user.service.DepartmentService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.messaging.InterviewNotificationMessage;
import club.boyuan.official.messaging.InterviewNotificationProducer;
import club.boyuan.official.persistence.entity.InterviewNotificationLog;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.InterviewNotificationLogMapper;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 方案B 的面试安排挂在场次上（slot_id 为空），邮件里的房间要从 interview_session 取。
 * 线上 2026 届 #14 周期 97 封「面试安排通知」的房间全是「待通知」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleNoticeLocationTest {

    @Mock private InterviewNotificationProducer notificationProducer;
    @Mock private InterviewNotificationLogMapper notificationLogMapper;
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
    @Mock private InterviewSessionMapper interviewSessionMapper;

    @InjectMocks
    private InterviewNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                InterviewNotificationLog.class);
        when(notificationLogMapper.selectCount(any())).thenReturn(0L);
        when(notificationLogMapper.insert(any(InterviewNotificationLog.class))).thenReturn(1);
        when(interviewSessionMapper.selectById(17)).thenReturn(
                new InterviewSession().setSessionId(17).setCycleId(14).setLocation("普陀校区 教书院 205"));
    }

    /** 方案B 的安排：没有 slot，只有 session */
    private static InterviewSchedule planB() {
        return new InterviewSchedule().setScheduleId(91).setResumeId(191).setCycleId(14)
                .setSlotId(null).setSessionId(17).setStatus(1)
                .setInterviewTime(LocalDateTime.of(2026, 10, 11, 9, 30));
    }

    @Test
    @DisplayName("方案B 安排：房间取场次的地点")
    void planBTakesLocationFromSession() {
        InterviewBookingDTO b = service.bookingOf(planB());
        assertEquals("普陀校区 教书院 205", b.getLocation());
        verify(interviewSlotService, never()).getById(any());
    }

    @Test
    @DisplayName("方案A 安排：仍以 slot 的地点为准，不去查场次")
    void planAKeepsSlotLocation() {
        InterviewSchedule a = planB().setSlotId(5).setSessionId(null);
        when(interviewSlotService.getById(5)).thenReturn(new InterviewSlot().setSlotId(5).setLocation("理科大楼 A301"));
        assertEquals("理科大楼 A301", service.bookingOf(a).getLocation());
        verify(interviewSessionMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("端到端：发出去的面试安排邮件里有房间，不再是「待通知」")
    void bookingSuccessMailCarriesRoom() {
        when(interviewScheduleService.getById(91)).thenReturn(planB());
        Resume resume = new Resume();
        resume.setResumeId(191);
        resume.setStatus(4);
        resume.setCycleId(14);
        when(resumeService.getResumeById(191)).thenReturn(resume);
        when(resumeDataService.getResumeEmail(resume)).thenReturn("10265101424@stu.ecnu.edu.cn");
        when(resumeDataService.getResumeName(resume)).thenReturn("周楷炎");

        service.deliver(new InterviewNotificationMessage(
                InterviewNotificationType.BOOKING_SUCCESS, 91, null, "req-room", null));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(messageUtils).sendHtmlEmail(anyString(), anyString(), html.capture(), text.capture());
        assertTrue(html.getValue().contains("普陀校区 教书院 205"), "HTML 版要有房间");
        assertFalse(html.getValue().contains("待通知"), "房间不该再是「待通知」");
        assertTrue(text.getValue().contains("面试地点：普陀校区 教书院 205"), "纯文本兜底版也要有");
    }
}
