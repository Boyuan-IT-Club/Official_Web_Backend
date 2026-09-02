package club.boyuan.official.infra.notification.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三封招新邮件的模板。邮件发出去不可撤回，这里守住几条不能错的：
 * 转义、纯文本兜底、缺配置时不印出 null。
 */
class RecruitmentMailsTest {

    @Test
    @DisplayName("面试提醒：时间地点都在，且 HTML 与纯文本都能读")
    void interviewReminderCarriesDetails() {
        var r = RecruitmentMails.interviewReminder(
                "丁华烨", "2025-09-27 13:40-13:50", "教书院206", "教书院202");

        assertTrue(r.html().contains("丁华烨"));
        assertTrue(r.html().contains("2025-09-27 13:40-13:50"));
        assertTrue(r.html().contains("教书院206"));
        assertTrue(r.html().contains("教书院202"), "候场教室要出现在正文里");
        assertTrue(r.plainText().contains("教书院202"),
                "纯文本兜底也要有 —— 关掉 HTML 的客户端只看得到它");
    }

    @Test
    @DisplayName("没配候场教室时整行不出现，而不是印一句「请提前抵达 null」")
    void missingWaitingRoomIsOmitted() {
        var r = RecruitmentMails.interviewReminder("张三", "2025-09-27 14:00-14:10", "教书院206", null);

        assertFalse(r.html().contains("候场教室"), "没配就不该出现这一行");
        assertFalse(r.html().contains("null"));
        assertFalse(r.plainText().contains("null"));
    }

    @Test
    @DisplayName("姓名里的尖括号被转义，不会破坏邮件结构")
    void nameIsEscaped() {
        var r = RecruitmentMails.interviewReminder(
                "<script>x</script>", "2025-09-27 14:00-14:10", "A101", "A102");

        assertFalse(r.html().contains("<script>"), "必须转义 —— 学生姓名是外部输入");
        assertTrue(r.html().contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("录取通知：带二维码时展示，且每张都有说明文字")
    void admittedShowsQrCodes() {
        var r = RecruitmentMails.admitted("李四", "2025-2026", "技术部", List.of(
                new MailTemplate.QrItem("https://cdn/dept.png", "技术部群"),
                new MailTemplate.QrItem("https://cdn/main.png", "社团大群")));

        assertTrue(r.html().contains("技术部"));
        assertTrue(r.html().contains("https://cdn/dept.png"));
        assertTrue(r.html().contains("alt=\"技术部群\""),
                "图要带 alt —— 客户端默认拦截外链图片，图没加载时得知道这是什么群");
        assertTrue(r.html().contains("社团大群"));
        assertTrue(r.plainText().contains("https://cdn/main.png"), "纯文本里给出链接");
    }

    @Test
    @DisplayName("录取通知：没配二维码时不留空块，改为提示去官网看")
    void admittedWithoutQrCodesDegrades() {
        var r = RecruitmentMails.admitted("王五", "2025-2026", "综合部", List.of());

        assertFalse(r.html().contains("<img src=\"\""), "不该留空图");
        assertTrue(r.html().contains("登录官网"), "要给出替代路径");
    }

    @Test
    @DisplayName("未录取：配了联系方式就附上，没配则整块不出现")
    void rejectedContactIsOptional() {
        var withContact = RecruitmentMails.rejected("赵六", "张三 微信 zhangsan");
        assertTrue(withContact.html().contains("张三 微信 zhangsan"));
        assertTrue(withContact.html().contains("本届负责人联系方式"));

        var without = RecruitmentMails.rejected("赵六", null);
        assertFalse(without.html().contains("本届负责人联系方式"));
        assertFalse(without.html().contains("null"));
    }

    @Test
    @DisplayName("未录取的措辞不写成「不合格」——这封信要体面")
    void rejectedToneStaysWarm() {
        var r = RecruitmentMails.rejected("孙七", null);
        assertFalse(r.html().contains("不合格"));
        assertFalse(r.html().contains("淘汰"));
        assertTrue(r.html().contains("欢迎"), "要留下后续参与的口子");
    }

    @Test
    @DisplayName("验证码邮件带上有效期，且不出现在标题以外的奇怪位置")
    void verificationCode() {
        var r = RecruitmentMails.verificationCode("482913", 5);
        assertTrue(r.html().contains("482913"));
        assertTrue(r.html().contains("5 分钟"));
        assertTrue(r.plainText().contains("482913"));
    }

    @Test
    @DisplayName("所有邮件都是完整 HTML 文档，且不含邮件客户端不支持的布局")
    void htmlIsEmailSafe() {
        var mails = List.of(
                RecruitmentMails.interviewReminder("甲", "t", "r", "w"),
                RecruitmentMails.admitted("乙", "2025-2026", "技术部", List.of()),
                RecruitmentMails.rejected("丙", "联系人"),
                RecruitmentMails.verificationCode("123456", 5));

        for (var m : mails) {
            assertTrue(m.html().startsWith("<!doctype html>"), "要是完整文档");
            assertTrue(m.html().contains("charset=\"utf-8\""), "缺 charset 中文会乱码");
            assertFalse(m.html().contains("display:flex"), "邮件客户端不支持 flex");
            assertFalse(m.html().contains("display:grid"), "邮件客户端不支持 grid");
            assertFalse(m.html().contains("<style"), "外置样式块会被剥掉，必须内联");
            assertFalse(m.plainText().isBlank(), "纯文本兜底不能为空");
        }
    }

}
