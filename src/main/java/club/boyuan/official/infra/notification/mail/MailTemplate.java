package club.boyuan.official.infra.notification.mail;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招新邮件的 HTML 骨架（「极简科技」版：白底 + 一条靛蓝强调线 + 大留白）。
 *
 * 为什么全是内联样式和表格布局：邮件客户端不是浏览器。QQ 邮箱、163、
 * Outlook 会剥掉 &lt;style&gt; 里的规则、flex、grid、自定义字体，外链 CSS
 * 一律不生效。所有排版只能靠 table + 内联 style，字体走系统中文栈。
 *
 * 每封邮件都同时产出 HTML 与纯文本兜底 —— 关掉 HTML 的客户端、邮件预览、
 * 读屏软件看到的是后者，不给的话他们只会看到一片空白。
 */
public final class MailTemplate {

    /** 社团 logo。邮件不能用 data URI（Gmail/Outlook 会剥掉），只能给绝对地址 */
    private static final String LOGO_URL = "https://official.boyuan.club/email-logo.png";
    private static final String SITE_URL = "https://official.boyuan.club";

    // 配色与选型页 B 版一致
    private static final String ACCENT = "#5B5BD6";
    private static final String INK = "#17181F";
    private static final String INK_2 = "#5B5F70";
    private static final String INK_3 = "#8B8FA3";
    private static final String LINE = "#EFEFF6";
    private static final String PANEL_BG = "#FAFAFE";
    private static final String PANEL_LINE = "#ECECF4";

    private static final String FONT =
            "'PingFang SC','Microsoft YaHei',-apple-system,BlinkMacSystemFont,sans-serif";

    private MailTemplate() {
    }

    /** 一封邮件：HTML 正文 + 纯文本兜底 */
    public record Rendered(String html, String plainText) {
    }

    public static Builder builder(String eyebrow, String title) {
        return new Builder(eyebrow, title);
    }

    public static final class Builder {
        private final String eyebrow;
        private final String title;
        private final List<String> blocks = new ArrayList<>();
        private final StringBuilder plain = new StringBuilder();

        private Builder(String eyebrow, String title) {
            this.eyebrow = eyebrow;
            this.title = title;
            plain.append(title).append("\n\n");
        }

        /** 一段正文。text 已是纯文本，HTML 侧会转义 */
        public Builder paragraph(String text) {
            if (!StringUtils.hasText(text)) {
                return this;
            }
            blocks.add("<tr><td style=\"padding:0 34px 16px;font-size:14.5px;color:" + INK_2
                    + ";line-height:1.85;\">" + nl2br(escape(text)) + "</td></tr>");
            plain.append(text).append("\n\n");
            return this;
        }

        /** 键值面板，用于面试时间/地点这类结构化信息 */
        public Builder infoPanel(Map<String, String> rows) {
            if (rows == null || rows.isEmpty()) {
                return this;
            }
            StringBuilder inner = new StringBuilder();
            rows.forEach((k, v) -> {
                inner.append("<tr>")
                        .append("<td width=\"88\" style=\"color:").append(INK_3)
                        .append(";padding:6px 0;font-size:14px;vertical-align:top;\">").append(escape(k)).append("</td>")
                        .append("<td style=\"color:").append(INK)
                        .append(";padding:6px 0;font-size:14px;font-weight:600;\">").append(nl2br(escape(v))).append("</td>")
                        .append("</tr>");
                plain.append(k).append("：").append(v).append('\n');
            });
            plain.append('\n');
            blocks.add("<tr><td style=\"padding:6px 34px 20px;\">"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "style=\"border:1px solid " + PANEL_LINE + ";border-radius:10px;background:" + PANEL_BG + ";\">"
                    + "<tr><td style=\"padding:18px 22px;\">"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
                    + inner + "</table></td></tr></table></td></tr>");
            return this;
        }

        /** 主按钮 */
        public Builder button(String text, String href) {
            blocks.add("<tr><td style=\"padding:4px 34px 20px;\">"
                    + "<a href=\"" + escape(href) + "\" style=\"display:inline-block;background:" + ACCENT
                    + ";color:#FFFFFF;font-size:14.5px;font-weight:600;text-decoration:none;"
                    + "padding:12px 26px;border-radius:8px;\">" + escape(text) + "</a></td></tr>");
            plain.append(text).append("：").append(href).append("\n\n");
            return this;
        }

        /**
         * 二维码组。
         *
         * 每张都配文字说明 —— 客户端默认拦截外链图片，图没加载时至少知道
         * 「这里本来有个什么群的码」，而不是一排破图标。
         */
        public Builder qrCodes(List<QrItem> items) {
            if (items == null || items.isEmpty()) {
                return this;
            }
            StringBuilder cells = new StringBuilder();
            for (QrItem it : items) {
                cells.append("<td align=\"center\" style=\"padding:0 10px;\">")
                        .append("<img src=\"").append(escape(it.imageUrl())).append("\" width=\"140\" alt=\"")
                        .append(escape(it.label())).append("\" style=\"display:block;width:140px;border:1px solid ")
                        .append(PANEL_LINE).append(";border-radius:8px;\">")
                        .append("<div style=\"font-size:13px;color:").append(INK_2)
                        .append(";margin-top:8px;\">").append(escape(it.label())).append("</div></td>");
                plain.append("· ").append(it.label()).append("：").append(it.imageUrl()).append('\n');
            }
            plain.append('\n');
            blocks.add("<tr><td style=\"padding:4px 34px 22px;\">"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                    + cells + "</tr></table>"
                    + "<div style=\"font-size:12.5px;color:" + INK_3 + ";margin-top:10px;\">"
                    + "若图片未显示，请在邮件客户端点击「显示图片」，或登录官网查看。</div></td></tr>");
            return this;
        }

        /** 分隔线 */
        public Builder divider() {
            blocks.add("<tr><td style=\"padding:0 34px;\"><div style=\"border-top:1px solid "
                    + LINE + ";\"></div></td></tr>");
            plain.append("--------\n");
            return this;
        }

        public Rendered build() {
            String body = String.join("", blocks);
            String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>" + escape(title) + "</title></head>"
                    + "<body style=\"margin:0;padding:24px 12px;background:#F5F5FA;\">"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td align=\"center\">"
                    + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "style=\"width:600px;max-width:100%;background:#FFFFFF;border:1px solid #E8E8F0;"
                    + "border-radius:12px;overflow:hidden;font-family:" + FONT + ";\">"
                    // 顶部靛蓝强调线
                    + "<tr><td style=\"height:4px;background:" + ACCENT + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
                    // 抬头：logo + 社名 + eyebrow
                    + "<tr><td style=\"padding:30px 34px 0;\">"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-bottom:20px;\"><tr>"
                    + "<td width=\"40\" style=\"vertical-align:middle;\">"
                    // alt 故意留空：右边紧挨着就是「博远信息技术社」这行文字，
                    // logo 在这里是装饰。给了 alt 反而会在图片被拦截时，
                    // 把四个字挤在 36px 宽里竖着堆成一列（实测过）。
                    + "<img src=\"" + LOGO_URL + "\" width=\"36\" alt=\"\" style=\"display:block;width:36px;border:0;\"></td>"
                    + "<td style=\"vertical-align:middle;padding-left:11px;\">"
                    + "<div style=\"font-size:14px;font-weight:600;color:" + INK + ";line-height:1.3;\">博远信息技术社</div>"
                    + "<div style=\"font-size:11px;letter-spacing:.14em;color:" + INK_3 + ";text-transform:uppercase;\">"
                    + escape(eyebrow) + "</div></td></tr></table>"
                    + "<div style=\"font-size:22px;font-weight:600;color:" + INK + ";line-height:1.4;margin-bottom:14px;\">"
                    + escape(title) + "</div></td></tr>"
                    + body
                    // 页脚
                    + "<tr><td style=\"padding:6px 34px 30px;\">"
                    + "<div style=\"border-top:1px solid " + LINE + ";margin-bottom:14px;\"></div>"
                    + "<div style=\"font-size:12.5px;color:" + INK_3 + ";line-height:1.75;\">"
                    + "华东师范大学 · 博远信息技术社<br>"
                    + "<a href=\"" + SITE_URL + "\" style=\"color:" + ACCENT + ";text-decoration:none;\">official.boyuan.club</a>"
                    + "<span style=\"color:#AEB2C2;\"> · 本邮件由系统自动发送</span>"
                    + "</div></td></tr>"
                    + "</table></td></tr></table></body></html>";

            plain.append("\n——华东师范大学 博远信息技术社\n").append(SITE_URL).append('\n');
            return new Rendered(html, plain.toString());
        }
    }

    /** 一张二维码：图片地址 + 说明文字 */
    public record QrItem(String imageUrl, String label) {
    }

    /** 供调用方按插入顺序组织信息面板 */
    public static Map<String, String> rows() {
        return new LinkedHashMap<>();
    }

    /**
     * HTML 转义。学生填的姓名、部门名都会进正文，不转义的话一个 &lt; 就能
     * 破坏整封邮件的结构（更别说注入）。
     */
    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 已转义文本里的换行转成 <br>，保留文案里的分行 */
    private static String nl2br(String escaped) {
        return escaped.replace("\n", "<br>");
    }
}
