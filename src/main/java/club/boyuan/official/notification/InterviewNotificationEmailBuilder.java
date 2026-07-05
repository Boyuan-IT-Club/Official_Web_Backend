package club.boyuan.official.notification;

import club.boyuan.official.dto.InterviewBookingDTO;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

/**
 * 面试通知邮件主题与正文构建。
 */
public final class InterviewNotificationEmailBuilder {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private InterviewNotificationEmailBuilder() {
    }

    public static String subject(InterviewNotificationType type) {
        return switch (type) {
            case BOOKING_SUCCESS -> "【博远信息技术社】面试预约成功通知";
            case EVE_REMINDER -> "【博远信息技术社】面试提醒（明日面试）";
            case DAY_REMINDER -> "【博远信息技术社】面试提醒（今日面试）";
            case ADMISSION -> "【博远信息技术社】面试录取通知";
            case REJECTION -> "【博远信息技术社】面试结果通知";
        };
    }

    public static String body(InterviewNotificationType type,
                              String recipientName,
                              InterviewBookingDTO booking,
                              String departmentName) {
        String greeting = greeting(recipientName);
        return switch (type) {
            case BOOKING_SUCCESS -> greeting + "\n\n"
                    + "您已成功预约博远信息技术社面试，详情如下：\n"
                    + formatBookingDetails(booking)
                    + "\n请准时参加，并登录官网查看预约详情。";
            case EVE_REMINDER -> greeting + "\n\n"
                    + "温馨提醒：您预约的面试将于明天进行，请提前做好准备。\n\n"
                    + formatBookingDetails(booking)
                    + "\n如需改期或取消，请尽快登录官网操作。";
            case DAY_REMINDER -> greeting + "\n\n"
                    + "今日面试提醒：您预约的面试将于今天进行，请准时到场/上线。\n\n"
                    + formatBookingDetails(booking)
                    + "\n祝您面试顺利！";
            case ADMISSION -> greeting + "\n\n"
                    + "恭喜您！经过综合评估，您已被博远信息技术社录取。\n"
                    + (StringUtils.hasText(departmentName)
                    ? "分配部门：" + departmentName + "\n"
                    : "")
                    + "\n后续入社安排请关注官网通知或社团后续联络，欢迎加入博远！";
            case REJECTION -> greeting + "\n\n"
                    + "感谢您对博远信息技术社的关注与参与。\n"
                    + "很遗憾，本次面试未能通过，期待未来有机会再次相见。\n"
                    + "\n祝您学业顺利！";
        };
    }

    private static String greeting(String recipientName) {
        if (StringUtils.hasText(recipientName)) {
            return recipientName + "，您好：";
        }
        return "您好：";
    }

    private static String formatBookingDetails(InterviewBookingDTO booking) {
        if (booking == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (booking.getInterviewTime() != null) {
            sb.append("面试时间：").append(booking.getInterviewTime().format(DATE_TIME_FMT)).append('\n');
        } else if (booking.getInterviewDate() != null) {
            sb.append("面试日期：").append(booking.getInterviewDate().format(DATE_FMT));
            if (booking.getStartTime() != null && booking.getEndTime() != null) {
                sb.append(' ')
                        .append(booking.getStartTime().format(TIME_FMT))
                        .append('-')
                        .append(booking.getEndTime().format(TIME_FMT));
            }
            sb.append('\n');
        }
        if (StringUtils.hasText(booking.getLocation())) {
            sb.append("面试地点：").append(booking.getLocation()).append('\n');
        }
        if (Integer.valueOf(2).equals(booking.getInterviewType()) && StringUtils.hasText(booking.getMeetingLink())) {
            sb.append("线上会议链接：").append(booking.getMeetingLink()).append('\n');
        }
        if (StringUtils.hasText(booking.getNotes())) {
            sb.append("备注：").append(booking.getNotes()).append('\n');
        }
        return sb.toString();
    }
}
