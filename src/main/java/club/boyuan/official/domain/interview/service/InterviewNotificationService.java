package club.boyuan.official.domain.interview.service;

import club.boyuan.official.infra.notification.InterviewNotificationType;

/**
 * 面试邮件通知：预约成功、定时提醒、录取/未录取。
 */
public interface InterviewNotificationService {

    /** 预约成功后异步发送邮件 */
    void enqueueBookingSuccess(Integer scheduleId, String requestId);

    /** 定时扫描并投递提醒（前一天 12:00 / 当天 8:00） */
    void dispatchReminders(InterviewNotificationType reminderType);

    /** 批量发送面试结果通知（录取/未录取） */
    void enqueueResultNotification(Integer resultId, String customBody);
}
