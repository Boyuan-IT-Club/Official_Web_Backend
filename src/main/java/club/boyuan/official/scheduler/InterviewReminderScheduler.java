package club.boyuan.official.scheduler;

import club.boyuan.official.config.InterviewNotificationProperties;
import club.boyuan.official.notification.InterviewNotificationType;
import club.boyuan.official.service.InterviewNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 面试提醒定时任务：前一天 12:00、当天 08:00（Asia/Shanghai）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewReminderScheduler {

    private final InterviewNotificationService notificationService;
    private final InterviewNotificationProperties properties;

    @Scheduled(cron = "${interview.notification.eve-reminder-cron:0 0 12 * * ?}",
            zone = "Asia/Shanghai")
    public void sendEveReminders() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("开始执行面试前一日提醒任务");
        notificationService.dispatchReminders(InterviewNotificationType.EVE_REMINDER);
    }

    @Scheduled(cron = "${interview.notification.day-reminder-cron:0 0 8 * * ?}",
            zone = "Asia/Shanghai")
    public void sendDayReminders() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("开始执行面试当日提醒任务");
        notificationService.dispatchReminders(InterviewNotificationType.DAY_REMINDER);
    }
}
