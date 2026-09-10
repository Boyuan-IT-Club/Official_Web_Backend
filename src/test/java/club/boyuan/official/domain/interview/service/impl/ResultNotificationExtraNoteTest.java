package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.infra.notification.InterviewNotificationEmailBuilder;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.infra.notification.mail.MailTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员在「发送结果通知」里填的内容是补充，不是替换。
 *
 * 线上表现过的错法：直接拿它当整封正文，学生收到的录取信里只剩管理员
 * 随手打的几个字，模板里的祝贺、分配部门、入群二维码全部丢失。
 */
class ResultNotificationExtraNoteTest {

    private static final String NOTE = "本周五 19:00 理科大楼 B226 见面会，请准时参加。";

    @Test
    @DisplayName("录取信：模板原文全在，补充说明附在后面")
    void admissionKeepsTemplateAndAppendsNote() {
        MailTemplate.Rendered r = InterviewNotificationEmailBuilder.html(
                InterviewNotificationType.ADMISSION, "李知遥", null, "技术部",
                "2027", null, List.of(), null, NOTE);

        // 模板原文
        assertTrue(r.html().contains("恭喜"), "录取模板的祝贺语不该消失");
        assertTrue(r.html().contains("技术部"), "分配部门不该消失");
        // 补充内容与其标题
        assertTrue(r.html().contains("社团补充说明"));
        assertTrue(r.html().contains("B226"));
    }

    @Test
    @DisplayName("未录取信：联系方式与补充说明共存")
    void rejectionKeepsTemplateAndAppendsNote() {
        MailTemplate.Rendered r = InterviewNotificationEmailBuilder.html(
                InterviewNotificationType.REJECTION, "王一帆", null, null,
                null, null, List.of(), "负责人 微信 abc", NOTE);

        assertTrue(r.html().contains("感谢"), "未录取模板正文不该消失");
        assertTrue(r.html().contains("负责人"), "周期配置的联系方式不该被挤掉");
        assertTrue(r.html().contains("社团补充说明"));
        assertTrue(r.html().contains("B226"));
    }

    @Test
    @DisplayName("纯文本兜底那份同样是「模板 + 补充」")
    void plainTextAlsoAppends() {
        String body = InterviewNotificationEmailBuilder.body(
                InterviewNotificationType.ADMISSION, "李知遥", null, "技术部", NOTE);

        assertTrue(body.contains("恭喜"));
        assertTrue(body.contains("技术部"));
        assertTrue(body.indexOf("社团补充说明") > body.indexOf("恭喜"), "补充说明必须在模板正文之后");
        assertTrue(body.contains("B226"));
    }

    @Test
    @DisplayName("没填补充内容时不出现空标题")
    void noNoteMeansNoSection() {
        MailTemplate.Rendered r = InterviewNotificationEmailBuilder.html(
                InterviewNotificationType.ADMISSION, "李知遥", null, "技术部",
                "2027", null, List.of(), null, "   ");
        assertFalse(r.html().contains("社团补充说明"));

        String body = InterviewNotificationEmailBuilder.body(
                InterviewNotificationType.ADMISSION, "李知遥", null, "技术部", null);
        assertFalse(body.contains("社团补充说明"));
    }
}
