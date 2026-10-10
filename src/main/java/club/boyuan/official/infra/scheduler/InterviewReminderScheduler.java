package club.boyuan.official.infra.scheduler;

import club.boyuan.official.infra.config.InterviewNotificationProperties;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.domain.interview.service.InterviewNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 面试提醒定时任务：前一天 12:00 与当天 08:00（均为 Asia/Shanghai）。
 *
 * 当日那一轮曾按业务要求下线过，理由是「对收到前一日提醒的人是重复打扰，
 * 而真正忘记的人也来不及改安排」。2026-10 招新重新启用：真正要防的是当天忘记、
 * 而不是让人改安排 —— 一封 08:00 的信够把人叫醒，首场 09:00 开始还来得及出门。
 *
 * 两轮共用 dispatchReminders，按类型决定目标日期（前一日取明天、当日取今天），
 * 并按 interview_notification_log 去重，所以同一轮重复执行不会重复发。
 * 线上面试不发任何模板信（见 InterviewNotificationServiceImpl 的说明）。
 *
 * 双机集群没有 leader 选举，两台都会起定时任务 —— Node B 把两个 cron 的环境变量
 * 设成 "-"（Spring 的 Scheduled.CRON_DISABLED）来关掉，只有 Node A 真正发信。
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
