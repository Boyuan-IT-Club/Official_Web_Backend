package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.infra.notification.InterviewNotificationEmailBuilder;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.infra.notification.mail.MailTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历初筛：状态常量语义与「未通过初筛」邮件文案。
 *
 * 初筛未通过与面试未录取是两批人、两封信：前者没进过面试，
 * 文案里不能出现「面试时你的表现」，否则收信人会以为自己面过。
 */
class ResumeScreeningTest {

    @Test
    @DisplayName("状态常量与数据库定义一致：4 通过初筛 / 5 未通过初筛")
    void statusConstantsMatchSchema() {
        assertEquals(2, ResumeServiceImpl.STATUS_SUBMITTED);
        assertEquals(4, ResumeServiceImpl.STATUS_SCREEN_PASSED);
        assertEquals(5, ResumeServiceImpl.STATUS_SCREEN_REJECTED);
    }

    @Test
    @DisplayName("初筛未通过邮件：说清流程到此为止，且不提面试表现")
    void resumeRejectedMailWording() {
        MailTemplate.Rendered r = InterviewNotificationEmailBuilder.html(
                InterviewNotificationType.RESUME_REJECTED, "李知遥", null, null,
                "2027", null, List.of(), "负责人 微信 abc", null);

        assertTrue(r.html().contains("未能进入面试环节"), "要说清没进面试");
        assertTrue(r.html().contains("负责人"), "周期配置的联系方式要在");
        // 这批人没面过试，出现「面试时」类措辞就是文案串了
        assertFalse(r.html().contains("面试时"), "不该出现面试表现相关措辞");
        assertFalse(r.html().contains("候场"), "不该出现面试现场相关信息");
    }

    @Test
    @DisplayName("初筛未通过邮件的标题与未录取区分开")
    void subjectDiffersFromRejection() {
        String screening = InterviewNotificationEmailBuilder.subject(InterviewNotificationType.RESUME_REJECTED);
        String rejection = InterviewNotificationEmailBuilder.subject(InterviewNotificationType.REJECTION);
        assertTrue(screening.contains("简历"), "标题要点明是简历评审结果");
        assertFalse(screening.equals(rejection));
    }

    @Test
    @DisplayName("补充说明同样附加在初筛邮件正文之后")
    void extraNoteAppended() {
        MailTemplate.Rendered r = InterviewNotificationEmailBuilder.html(
                InterviewNotificationType.RESUME_REJECTED, "李知遥", null, null,
                null, null, List.of(), null, "欢迎参加 12 月的技术分享会");
        assertTrue(r.html().contains("未能进入面试环节"));
        assertTrue(r.html().contains("社团补充说明"));
        assertTrue(r.html().contains("技术分享会"));
    }
}
