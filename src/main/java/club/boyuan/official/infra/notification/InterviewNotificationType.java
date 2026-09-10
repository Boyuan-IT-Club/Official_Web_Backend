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
    REJECTION,

    /**
     * 简历未通过初筛。
     *
     * 与 REJECTION 的区别：这封在面试之前发出，收件人根本没进过面试环节，
     * 因此文案不能提「面试时你的表现」，也不该出现候场、改期一类的字眼。
     */
    RESUME_REJECTED
}
