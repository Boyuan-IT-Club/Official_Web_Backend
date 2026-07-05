package club.boyuan.official.notification;

import club.boyuan.official.dto.InterviewBookingDTO;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;

/**
 * 面试通知邮件主题与 HTML 正文构建。
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

    public static String htmlBody(InterviewNotificationType type,
                                  String recipientName,
                                  InterviewBookingDTO booking,
                                  String departmentName) {
        String name = displayName(recipientName);
        return switch (type) {
            case BOOKING_SUCCESS -> page(
                    "#1677ff",
                    "面试预约成功",
                    name + "，您的面试预约已确认",
                    "我们已为您安排好面试时间与地点，请按时参加，并提前确认交通或线上会议环境。",
                    bookingDetailsCard(booking),
                    "请登录官网查看预约详情。如需改期或取消，请尽快在官网操作。"
            );
            case EVE_REMINDER -> page(
                    "#7c3aed",
                    "明日面试提醒",
                    name + "，您预约的面试将在明天进行",
                    "请提前准备自我介绍、项目经历和常用联系方式，合理安排到场或上线时间。",
                    bookingDetailsCard(booking),
                    "如临时无法参加，请尽快登录官网改期或取消。"
            );
            case DAY_REMINDER -> page(
                    "#d97706",
                    "今日面试提醒",
                    name + "，您预约的面试将在今天进行",
                    "请准时到场或上线，建议提前 10 分钟完成签到、设备检查和材料准备。",
                    bookingDetailsCard(booking),
                    "祝您面试顺利。"
            );
            case ADMISSION -> page(
                    "#16a34a",
                    "面试录取通知",
                    name + "，恭喜您通过本次面试",
                    admissionMessage(departmentName),
                    bookingDetailsCard(booking),
                    "后续入社安排请关注官网通知或社团后续联络，欢迎加入博远信息技术社。"
            );
            case REJECTION -> page(
                    "#475569",
                    "面试结果通知",
                    name + "，感谢您参与本次面试",
                    "经过综合评估，本次面试暂未通过。感谢您对博远信息技术社的关注与投入，也期待未来有机会再次交流。",
                    "",
                    "祝您学业顺利，也欢迎继续关注博远信息技术社后续活动。"
            );
        };
    }

    public static String customHtmlBody(String recipientName, String customBody, InterviewBookingDTO booking) {
        return page(
                "#1677ff",
                "面试结果通知",
                displayName(recipientName) + "，请查收您的面试结果通知",
                nl2br(customBody),
                bookingDetailsCard(booking),
                "此邮件由博远信息技术社官网系统发送。"
        );
    }

    private static String admissionMessage(String departmentName) {
        if (!StringUtils.hasText(departmentName)) {
            return "经过综合评估，您已被博远信息技术社录取。";
        }
        return "经过综合评估，您已被博远信息技术社录取。<br>"
                + "分配部门：<strong>" + escape(departmentName) + "</strong>";
    }

    private static String bookingDetailsCard(InterviewBookingDTO booking) {
        if (booking == null) {
            return "";
        }
        StringBuilder rows = new StringBuilder();
        if (booking.getInterviewTime() != null) {
            rows.append(row("面试时间", booking.getInterviewTime().format(DATE_TIME_FMT)));
        } else if (booking.getInterviewDate() != null) {
            StringBuilder value = new StringBuilder(booking.getInterviewDate().format(DATE_FMT));
            if (booking.getStartTime() != null && booking.getEndTime() != null) {
                value.append(' ')
                        .append(booking.getStartTime().format(TIME_FMT))
                        .append('-')
                        .append(booking.getEndTime().format(TIME_FMT));
            }
            rows.append(row("面试时间", value.toString()));
        }
        if (StringUtils.hasText(booking.getLocation())) {
            rows.append(row("面试地点", booking.getLocation()));
        }
        if (Integer.valueOf(2).equals(booking.getInterviewType()) && StringUtils.hasText(booking.getMeetingLink())) {
            rows.append(linkRow("线上会议链接", booking.getMeetingLink()));
        }
        if (StringUtils.hasText(booking.getNotes())) {
            rows.append(row("备注", booking.getNotes()));
        }
        if (rows.isEmpty()) {
            return "";
        }
        return """
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:22px 0;border-collapse:collapse;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;">
                  <tbody>%s</tbody>
                </table>
                """.formatted(rows);
    }

    private static String row(String label, String value) {
        return """
                <tr>
                  <td style="width:108px;padding:12px 14px;border-bottom:1px solid #e2e8f0;color:#64748b;font-size:14px;">%s</td>
                  <td style="padding:12px 14px;border-bottom:1px solid #e2e8f0;color:#0f172a;font-size:14px;font-weight:600;">%s</td>
                </tr>
                """.formatted(escape(label), escape(value));
    }

    private static String linkRow(String label, String url) {
        String safeUrl = escape(url);
        return """
                <tr>
                  <td style="width:108px;padding:12px 14px;border-bottom:1px solid #e2e8f0;color:#64748b;font-size:14px;">%s</td>
                  <td style="padding:12px 14px;border-bottom:1px solid #e2e8f0;color:#0f172a;font-size:14px;font-weight:600;">
                    <a href="%s" style="color:#1677ff;text-decoration:none;">%s</a>
                  </td>
                </tr>
                """.formatted(escape(label), safeUrl, safeUrl);
    }

    private static String page(String accent,
                               String eyebrow,
                               String title,
                               String message,
                               String detailHtml,
                               String footerText) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',Arial,sans-serif;color:#0f172a;">
                  <div style="display:none;max-height:0;overflow:hidden;color:transparent;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f5f9;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border:1px solid #e2e8f0;border-radius:10px;overflow:hidden;">
                          <tr>
                            <td style="height:6px;background:%s;"></td>
                          </tr>
                          <tr>
                            <td style="padding:30px 32px 26px;">
                              <div style="font-size:13px;font-weight:700;color:%s;letter-spacing:0;margin-bottom:10px;">%s</div>
                              <h1 style="margin:0 0 16px;font-size:24px;line-height:1.35;color:#0f172a;font-weight:700;">%s</h1>
                              <p style="margin:0;color:#334155;font-size:15px;line-height:1.8;">%s</p>
                              %s
                              <p style="margin:18px 0 0;color:#475569;font-size:14px;line-height:1.8;">%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px;background:#f8fafc;color:#64748b;font-size:12px;line-height:1.7;border-top:1px solid #e2e8f0;">
                              此邮件由博远信息技术社官网系统自动发送，请勿直接回复。
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(eyebrow),
                escape(title),
                escape(accent),
                escape(accent),
                escape(eyebrow),
                escape(title),
                message,
                detailHtml == null ? "" : detailHtml,
                escape(footerText)
        );
    }

    private static String displayName(String recipientName) {
        if (StringUtils.hasText(recipientName)) {
            return recipientName.trim();
        }
        return "同学";
    }

    private static String nl2br(String value) {
        return escape(value).replace("\n", "<br>");
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
