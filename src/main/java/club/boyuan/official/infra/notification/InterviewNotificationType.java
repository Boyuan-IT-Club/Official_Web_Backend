package club.boyuan.official.infra.notification;

/**
 * 面试相关邮件通知类型。
 */
public enum InterviewNotificationType {

    /** 预约成功后立即发送 */
    BOOKING_SUCCESS,

    /** 面试前一天 12:00 提醒 */
    EVE_REMINDER,

    /** 面试当天 08:00 提醒 */
    DAY_REMINDER,

    /** 录取通知（含分配部门） */
    ADMISSION,

    /** 未录取通知 */
    REJECTION
}
