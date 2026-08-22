package club.boyuan.official.infra.scheduler;

import club.boyuan.official.infra.config.InterviewNotificationProperties;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.domain.interview.service.InterviewNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 面试提醒定时任务：前一天 12:00（Asia/Shanghai）。
 *
 * 只保留前一日提醒。当日 08:00 那一轮已按业务要求下线 —— 面试当天早上再发一封，
 * 对已经收到前一日提醒的人是重复打扰，而真正忘记的人也来不及改安排。
 * InterviewNotificationType.DAY_REMINDER 与它的邮件模板保留未删：
 * dispatchReminders 仍接受该类型，将来若要恢复，只需在此加回一个 @Scheduled 方法。
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
}
