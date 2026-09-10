package club.boyuan.official.infra.notification.mail;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 三封招新邮件的文案与结构。文案沿用社团既有说法，只在必要处补上
 * 「登录官网」这类可操作的指引。
 */
public final class RecruitmentMails {

    private static final String PROGRESS_URL = "https://official.boyuan.club/main/interview-appointment";

    private RecruitmentMails() {
    }

    /**
     * 面试提醒。
     *
     * @param name         候选人姓名
     * @param timeText     形如 2025-09-27 13:40-13:50
     * @param room         面试房间（即面试地点）
     * @param waitingRoom  候场教室，按周期配置；没配就不出现这一行，
     *                     而不是印一句「请提前抵达 null」
     */
    public static MailTemplate.Rendered interviewReminder(
            String name, String timeText, String room, String waitingRoom) {

        Map<String, String> rows = MailTemplate.rows();
        rows.put("面试时间", nz(timeText, "待通知"));
        rows.put("面试房间", nz(room, "待通知"));
        if (StringUtils.hasText(waitingRoom)) {
            rows.put("候场教室", waitingRoom + "\n请提前 10 分钟抵达候场");
        }

        return MailTemplate.builder("Interview Notice", "面试安排已确认")
                .paragraph("未来的博远er " + nz(name, "同学") + "，你好哇~\n感谢报名博远信息技术社！")
                .infoPanel(rows)
                .paragraph("如因时间冲突或其他特殊情况无法到场，请登录官网「申请进度」提交改期申请，"
                        + "我们会为你调整合适的时间，或安排线上面试。")
                .button("前往申请进度", PROGRESS_URL)
                .paragraph("预祝面试顺利！")
                .build();
    }

    /**
     * 录取通知。
     *
     * @param deptName 录取部门
     * @param qrCodes  部门群 + 大群二维码。没配置时整块不出现，
     *                 但正文仍会提示可登录官网查看 —— 免得学生以为漏发了
     */
    public static MailTemplate.Rendered admitted(
            String name, String academicYear, String deptName, List<MailTemplate.QrItem> qrCodes) {
        return admitted(name, academicYear, deptName, qrCodes, null);
    }

    /**
     * @param extraNote 管理员在发送时填的补充内容。<b>附在模板正文之后</b>，
     *                  不替换原文——管理员想说的是"另外还有一件事"，
     *                  而不是"这封信别的都不要了"（线上实测过：早期实现直接
     *                  覆盖，学生收到的录取信里只剩管理员随手打的几个字）
     */
    public static MailTemplate.Rendered admitted(
            String name, String academicYear, String deptName,
            List<MailTemplate.QrItem> qrCodes, String extraNote) {

        MailTemplate.Builder b = MailTemplate.builder("Admission Notice", "欢迎加入博远信息技术社")
                .paragraph(nz(name, "同学") + "：\n你好！感谢你积极参与博远招新系列活动，"
                        + "并对我们的工作给予了极大的支持与帮助。")
                .paragraph("但是我们要很遗憾地告诉你——你要被我们抓来干活了！（bushi）")
                .paragraph("首先恭喜你通过简历、面试的层层考查，经过商讨后成功加入"
                        + "博远信息技术社 " + nz(academicYear, "本") + "学年 " + nz(deptName, "") + "！")
                .paragraph("祝愿你在社团中有所归属、有所进步、有所成就。");

        if (qrCodes != null && !qrCodes.isEmpty()) {
            b.paragraph("请扫码入群，参与日常商讨，做大做强就靠你我（doge）")
                    .qrCodes(qrCodes);
        } else {
            b.paragraph("入群二维码稍后奉上，也可以登录官网「申请进度」随时查看。");
        }

        b.button("登录官网查看", PROGRESS_URL);
        appendExtraNote(b, extraNote);
        return b.build();
    }

    /**
     * 未录取通知。
     *
     * @param contactInfo 本届负责人联系方式，按周期配置
     */
    public static MailTemplate.Rendered rejected(String name, String contactInfo) {
        return rejected(name, contactInfo, null);
    }

    /** @param extraNote 管理员补充内容，附在正文之后（不替换原文） */
    public static MailTemplate.Rendered rejected(String name, String contactInfo, String extraNote) {
        MailTemplate.Builder b = MailTemplate.builder("Application Result", "感谢你参与博远招新")
                .paragraph(nz(name, "同学") + "，你好：\n感谢你积极参与博远招新系列活动，"
                        + "并对我们的工作给予了极大的支持和帮助。")
                .paragraph("面试时你的优异表现我们有目共睹，但出于社团整体规划的考虑，"
                        + "我们认为你与社团目前可能并不是十分契合。")
                .paragraph("但是！但是！但是！\n我们不想放弃每一位优秀的同学，"
                        + "以及愿意和我们一起建设社团的同志。")
                .paragraph("社团永远为有热情、有想法的同学敞开。欢迎参与技术分享、学习小组，"
                        + "以及寒假的 OwnerPro 活动——在各类活动中表现积极的同学，社团也都会考虑吸纳。")
                .paragraph("欢迎随时关注我们的外部群和公众号了解最新动态。有任何问题都能在群里提问，"
                        + "活动信息也会同步到群里；有技术相关的需求或项目招募，也欢迎联系我们。");

        if (StringUtils.hasText(contactInfo)) {
            b.divider().paragraph("本届负责人联系方式\n" + contactInfo);
        }
        appendExtraNote(b, extraNote);
        return b.build();
    }

    /**
     * 管理员补充内容统一的呈现方式：分隔线 + 「社团补充说明」小标题 + 原样正文。
     * 加标题是为了让收件人分得清哪段是模板、哪段是这次特意写的。
     */
    private static void appendExtraNote(MailTemplate.Builder b, String extraNote) {
        if (!StringUtils.hasText(extraNote)) {
            return;
        }
        b.divider().paragraph("社团补充说明\n" + extraNote.trim());
    }

    /**
     * 简历未通过初筛。
     *
     * 与「未录取」分开写：这批同学没进过面试，文案里不能出现
     * 「面试时你的表现」；同时明确告诉他们本届流程到此为止，
     * 免得继续等面试通知。
     */
    public static MailTemplate.Rendered resumeRejected(String name, String contactInfo, String extraNote) {
        MailTemplate.Builder b = MailTemplate.builder("Application Result", "感谢你投递博远信息技术社")
                .paragraph(nz(name, "同学") + "，你好：\n感谢你投递博远信息技术社招新简历，"
                        + "也感谢你愿意花时间了解我们。")
                .paragraph("经过简历评审，很遗憾，本次你的简历未能进入面试环节，"
                        + "本届招新流程到此结束。")
                .paragraph("这并不代表对你能力的否定——名额与方向的匹配往往比能力本身更受限。"
                        + "社团的技术分享、学习小组与寒假 OwnerPro 活动都对所有同学开放，"
                        + "欢迎继续参与，我们也会在活动中留意积极的同学。")
                .paragraph("期待下一届与你相遇。");

        if (StringUtils.hasText(contactInfo)) {
            b.divider().paragraph("本届负责人联系方式\n" + contactInfo);
        }
        appendExtraNote(b, extraNote);
        return b.build();
    }

    /** 验证码。与三封通知同一套视觉，不再是一行裸文本 */
    public static MailTemplate.Rendered verificationCode(String code, int validMinutes) {
        return MailTemplate.builder("Verification Code", "你的验证码")
                .paragraph("请在页面中输入以下验证码完成验证：")
                .infoPanel(Map.of("验证码", code))
                .paragraph("验证码 " + validMinutes + " 分钟内有效。"
                        + "如果这不是你本人的操作，忽略本邮件即可，你的账号不会有任何变化。")
                .build();
    }

    private static String nz(String v, String fallback) {
        return StringUtils.hasText(v) ? v : fallback;
    }
}
