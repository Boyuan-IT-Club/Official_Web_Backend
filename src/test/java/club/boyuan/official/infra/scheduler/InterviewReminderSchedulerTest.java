package club.boyuan.official.infra.scheduler;

import club.boyuan.official.domain.interview.service.InterviewNotificationService;
import club.boyuan.official.infra.config.InterviewNotificationProperties;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 面试提醒的两轮定时任务。
 *
 * <p>当日那一轮曾被整段删掉过一次（当时的业务判断是「重复打扰」），
 * 2026-10 招新又要回来了。这里把两轮都钉住，免得下次再被顺手拿掉。
 */
class InterviewReminderSchedulerTest {

    private InterviewNotificationService notificationService;
    private InterviewNotificationProperties properties;
    private InterviewReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        notificationService = mock(InterviewNotificationService.class);
        properties = new InterviewNotificationProperties();
        scheduler = new InterviewReminderScheduler(notificationService, properties);
    }

    private static String cronOf(String method) throws Exception {
        Method m = InterviewReminderScheduler.class.getMethod(method);
        Scheduled s = m.getAnnotation(Scheduled.class);
        assertNotNull(s, method + " 应当是定时任务");
        assertEquals("Asia/Shanghai", s.zone(), "面试时间是北京时间，时区不能漏");
        return s.cron();
    }

    @Test
    @DisplayName("当日提醒：当天 08:00，首场 09:00 开始，来得及出门")
    void dayReminderRunsAtEight() throws Exception {
        // 占位符带默认值：Node B 靠环境变量把它设成 "-" 关掉，默认值必须是能跑的
        assertEquals("${interview.notification.day-reminder-cron:0 0 8 * * ?}", cronOf("sendDayReminders"));
        assertEquals("0 0 8 * * ?", properties.getDayReminderCron());
    }

    @Test
    @DisplayName("前一日提醒照旧：前一天 12:00")
    void eveReminderUnchanged() throws Exception {
        assertEquals("${interview.notification.eve-reminder-cron:0 0 12 * * ?}", cronOf("sendEveReminders"));
        assertEquals("0 0 12 * * ?", properties.getEveReminderCron());
    }

    @Test
    @DisplayName("两轮各自投递自己的类型，别串了")
    void dispatchesItsOwnType() {
        scheduler.sendDayReminders();
        verify(notificationService).dispatchReminders(InterviewNotificationType.DAY_REMINDER);

        scheduler.sendEveReminders();
        verify(notificationService).dispatchReminders(InterviewNotificationType.EVE_REMINDER);
    }

    @Test
    @DisplayName("总开关关掉时两轮都不发")
    void respectsMasterSwitch() {
        properties.setEnabled(false);

        scheduler.sendDayReminders();
        scheduler.sendEveReminders();

        verify(notificationService, never()).dispatchReminders(org.mockito.ArgumentMatchers.any());
    }
}
