package club.boyuan.official.common.utils;

import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.ResumeFieldValueDTO;
import club.boyuan.official.domain.resume.dto.SimpleResumeFieldDTO;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import com.itextpdf.text.*;
import com.itextpdf.text.SplitCharacter;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.DefaultSplitCharacter;
import com.itextpdf.text.pdf.PdfChunk;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * PDF导出工具类
 * 用于将简历数据导出为PDF格式
 */
@Slf4j
public class PdfExportUtil {

    /**
     * 中文字体解析一次就够：解析要枚举 TTC 子字体、逐个探针验字形，
     * 而每份 PDF 会取六七种字号，不缓存等于每次导出重跑几十遍。
     */
    private static volatile BaseFont CHINESE_BASE_FONT;
    private static volatile boolean FONT_RESOLVED;
    private static final Object FONT_LOCK = new Object();

    /**
     * 候选字体文件，按「一定有简体字形」的可信度排序。
     * 容器里装的是 fonts-noto-cjk（见 Dockerfile），Noto 排最前；
     * 后面几条是本地开发环境（macOS / Windows）用的，生产不会命中。
     * 具体用 TTC 里的哪个子字体不写死索引，交给 expandFontFile 按名字挑。
     */
    private static final String[] FONT_FILE_CANDIDATES = {
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-SC-Regular.otf",
            "/usr/share/fonts/opentype/noto/NotoSerifCJK-SC-Regular.otf",
            // 本地开发环境。这里黑体排在宋体前面，是为了跟生产对齐：
            // 容器里命中的是 Noto **Sans** CJK，本地若先选到宋体，
            // 调版式时看到的字面观感和线上不是一回事。
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/STHeiti Medium.ttc",
            "/System/Library/Fonts/Supplemental/Songti.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc",
    };

    /**
     * 将简历数据导出为PDF格式
     * @param resumeDTO 简历数据传输对象
     * @return PDF字节数组
     * @throws BusinessException 导出失败时抛出业务异常
     */
    public static byte[] exportResumeToPdf(ResumeDTO resumeDTO) throws BusinessException {
        try {
            // 在开始之前检查字体可用性
            System.out.println("开始初始化PDF字体...");
            BaseFont testFont = getChineseBaseFont();
            
            // 检查简历数据是否为空
            if (resumeDTO == null) {
                throw new BusinessException(BusinessExceptionEnum.EXPORT_PDF_FAILED, "PDF导出失败: 简历数据为空");
            }
            
            System.out.println("开始导出PDF，用户ID: " + resumeDTO.getUserId());
            if (resumeDTO.getSimpleFields() != null) {
                System.out.println("简历字段数量: " + resumeDTO.getSimpleFields().size());
            } else {
                System.out.println("警告: 简历字段为null");
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Document document = new Document(PageSize.A4, 48, 48, 44, 52);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            // 页码画在每页底部：项目经验写得长时简历会有两三页，没页码的多页文档在
            // 打印出来传阅时很容易乱序
            writer.setPageEvent(new FooterPageEvent());
            document.open();

            BaseColor brand = new BaseColor(31, 58, 96);      // 深蓝
            BaseColor accent = new BaseColor(31, 118, 204);   // 品牌蓝
            BaseColor lightLine = new BaseColor(225, 232, 240);
            BaseColor subText = new BaseColor(110, 120, 135);

            Font sectionFont = getFont(12, Font.BOLD, accent);
            Font labelFont = getFont(9, Font.NORMAL, subText);
            Font valueFont = getFont(11, Font.NORMAL, new BaseColor(35, 40, 48));
            Font bodyFont = getFont(10, Font.NORMAL, new BaseColor(55, 62, 72));
            Font footFont = getFont(8, Font.NORMAL, subText);
            Font chipFont = getFont(9, Font.NORMAL, new BaseColor(31, 58, 96));
            BaseColor chipBg = new BaseColor(234, 239, 247);

            try {
                java.util.Map<String, String> byKey = new java.util.LinkedHashMap<>();
                java.util.List<SimpleResumeFieldDTO> fields = resumeDTO.getSimpleFields() != null
                        ? resumeDTO.getSimpleFields() : new ArrayList<>();
                Image photoImage = null;
                for (SimpleResumeFieldDTO f : fields) {
                    if (f.getFieldKey() != null) byKey.put(f.getFieldKey(), f.getFieldValue());
                    if (photoImage == null && isBase64Image(f.getFieldValue())) {
                        photoImage = createImageFromBase64(f.getFieldValue());
                    }
                }

                String name = firstNonBlank(byKey.get("name"), "未填写姓名");

                // ── 页眉：大号姓名 + 品牌色副标题 + 品牌色细线 ──
                // 原先是整块深蓝横幅，打印/黑白复印时一团黑；改为留白页眉更耐看，
                // 与前端 Word 导出（exportResume.ts）保持同一版式语言
                PdfPTable head = new PdfPTable(photoImage != null ? new float[]{4f, 1f} : new float[]{1f});
                head.setWidthPercentage(100);
                PdfPCell hc = new PdfPCell();
                hc.setBorder(Rectangle.NO_BORDER);
                hc.setPaddingLeft(0);
                Paragraph bt = new Paragraph(name, getFont(24, Font.BOLD, new BaseColor(35, 40, 48)));
                Paragraph bs = new Paragraph("博远信息技术社 · 招新申请简历", getFont(10, Font.NORMAL, accent));
                bs.setSpacingBefore(5);
                // 社徽跟在副标题这一行的行首，和文字同高（12pt），
                // 不单独占一行——简历的主角是姓名，logo 只是署名
                Image mark = loadBrandLogo();
                if (mark != null) {
                    mark.scaleToFit(14f, 14f);
                    Chunk markChunk = new Chunk(mark, 0, -3f, true);
                    bs.add(0, new Chunk("  "));
                    bs.add(0, markChunk);
                }
                hc.addElement(bt);
                hc.addElement(bs);
                head.addCell(hc);
                if (photoImage != null) {
                    PdfPCell hp = new PdfPCell(photoImage, true);
                    hp.setBorder(Rectangle.BOX);
                    hp.setBorderColor(lightLine);
                    hp.setBorderWidth(0.8f);
                    hp.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    hp.setPadding(3f);
                    head.addCell(hp);
                }
                head.setSpacingAfter(6);
                document.add(head);

                // 品牌色分隔线
                PdfPTable rule = new PdfPTable(1);
                rule.setWidthPercentage(100);
                PdfPCell rc = new PdfPCell();
                rc.setBorder(Rectangle.BOTTOM);
                rc.setBorderColorBottom(accent);
                rc.setBorderWidthBottom(1.6f);
                rc.setFixedHeight(2f);
                rule.addCell(rc);
                rule.setSpacingAfter(12);
                document.add(rule);

                // ── 基本信息（双列，空字段也列出标签——空草稿导出不再是"什么都没有"）──
                // 顺序与网页、Word 导出一致（学号 → 性别 → 年级 → 专业 → 邮箱 →
                // 手机 → GitHub）。三处此前各有一套写死的顺序，同一份简历
                // 在网页、PDF、Word 里长得都不一样。
                java.util.List<String[]> basics = new ArrayList<>();
                addBasic(basics, "学号", byKey.get("student_id"));
                addBasic(basics, "性别", byKey.get("gender"));
                addBasic(basics, "年级", byKey.get("grade"));
                addBasic(basics, "专业", byKey.get("major"));
                addBasic(basics, "邮箱", byKey.get("email"));
                addBasic(basics, "手机", byKey.get("phone"));
                addBasic(basics, "GitHub", byKey.get("github"));

                addBasicGrid(document, basics, labelFont, valueFont);

                // ── 长文本小节 ──────────────────────────────
                // 小节顺序同样对齐：自我介绍 → 加入理由 → 个人简介 → 期望部门 →
                // 技术栈 → 项目经验。
                //
                // 自我介绍与个人简介拆成两节：原来是 firstNonBlank(两者)，
                // 学生两个都填时后一个被静默丢掉（网页与 Word 导出有同样的毛病，
                // 已一并修掉）。
                addSection(document, "自我介绍", byKey.get("self_introduction"), sectionFont, bodyFont, accent);
                addSection(document, "加入理由", byKey.get("reason"), sectionFont, bodyFont, accent);
                addSection(document, "个人简介", byKey.get("introduction"), sectionFont, bodyFont, accent);
                addChipSection(document, "期望部门", byKey.get("expected_departments"),
                        sectionFont, chipFont, accent, chipBg);
                addChipSection(document, "技术栈", byKey.get("tech_stack"),
                        sectionFont, chipFont, accent, chipBg);
                addSection(document, "项目经验", byKey.get("project_experience"), sectionFont, bodyFont, accent);

                // 其余未归类字段（模板可扩展，逐条列出）
                java.util.Set<String> known = new java.util.HashSet<>(java.util.Arrays.asList(
                        "name", "student_id", "email", "phone", "grade", "gender", "major", "github",
                        "expected_departments", "tech_stack", "self_introduction", "introduction",
                        "project_experience", "reason", "personal_photo"));
                StringBuilder extras = new StringBuilder();
                // 自定义字段按管理员配置的 sort_order 排，别按数据库返回的偶然顺序
                java.util.List<SimpleResumeFieldDTO> orderedExtras = new ArrayList<>(fields);
                orderedExtras.sort(java.util.Comparator.comparing(
                        f -> f.getSortOrder() == null ? Integer.MAX_VALUE : f.getSortOrder()));
                for (SimpleResumeFieldDTO f : orderedExtras) {
                    if (f.getFieldKey() == null || known.contains(f.getFieldKey())) continue;
                    if (f.getFieldValue() == null || f.getFieldValue().trim().isEmpty()) continue;
                    if (isBase64Image(f.getFieldValue())) continue;
                    if (extras.length() > 0) extras.append("\n");
                    extras.append(f.getFieldLabel() != null ? f.getFieldLabel() : f.getFieldKey())
                          .append("：").append(joinIfJsonArray(f.getFieldValue()));
                }
                if (extras.length() > 0) {
                    addSection(document, "其他信息", extras.toString(), sectionFont, bodyFont, accent);
                }

                // 提交/导出信息放在正文末尾；页码由 PageEvent 画在每页底部
                Paragraph foot = new Paragraph(
                        "状态：" + getStatusText(resumeDTO.getStatus())
                                + (resumeDTO.getSubmittedAt() != null
                                        ? "    提交时间：" + formatDateTime(resumeDTO.getSubmittedAt()) : "")
                                + "    导出时间：" + formatDateTime(LocalDateTime.now()),
                        footFont);
                foot.setAlignment(Element.ALIGN_RIGHT);
                foot.setSpacingBefore(22);
                document.add(foot);

            } finally {
                document.close();
            }
            
            byte[] pdfBytes = baos.toByteArray();
            System.out.println("PDF导出成功，文件大小: " + pdfBytes.length + " bytes");
            return pdfBytes;
        } catch (Exception e) {
            System.err.println("PDF导出失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            
            // 提供更详细的错误信息
            if (e.getMessage() != null && e.getMessage().contains("FontManager")) {
                throw new BusinessException(BusinessExceptionEnum.EXPORT_PDF_FAILED, 
                    "PDF导出失败: 字体初始化错误，请联系管理员检查服务器配置");
            } else {
                throw new BusinessException(BusinessExceptionEnum.EXPORT_PDF_FAILED, 
                    "PDF导出失败: " + e.getMessage());
            }
        }
    }
    
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }

    /** JSON 数组字符串转顿号分隔；非数组原样返回 */
    private static String joinIfJsonArray(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]")) {
            try {
                String inner = t.substring(1, t.length() - 1);
                if (inner.trim().isEmpty()) return "";
                String[] parts = inner.split(",");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    String v = part.trim();
                    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                        v = v.substring(1, v.length() - 1);
                    }
                    if (v.isEmpty()) continue;
                    if (sb.length() > 0) sb.append("、");
                    sb.append(v);
                }
                return sb.toString();
            } catch (Exception ignored) { /* 原样返回 */ }
        }
        return raw;
    }

    private static void addBasic(java.util.List<String[]> list, String label, String value) {
        // 空字段也列出标签：空草稿导出的 PDF 至少能看出结构，而不是「一片空白」
        list.add(new String[]{label, value != null && !value.trim().isEmpty() ? value : "未填写"});
    }

    /**
     * 基本信息排成「标签｜值｜标签｜值」四列网格。
     *
     * 原先是左右两个单元格各自竖着堆 "标签  值" 的段落——标签宽度不一样，
     * 值的起始位置就跟着飘：右列「邮箱/手机」对得上，轮到更宽的「GitHub」
     * 整行就被顶出去，一眼看去参差不齐。
     * 现在标签列宽度固定、右对齐，值列左对齐，两边各自成栏。
     */
    private static void addBasicGrid(Document document, java.util.List<String[]> items,
                                     Font labelFont, Font valueFont) throws DocumentException {
        if (items.isEmpty()) {
            return;
        }
        PdfPTable grid = new PdfPTable(new float[]{1.1f, 3.4f, 1.1f, 3.4f});
        grid.setWidthPercentage(100);
        grid.setSpacingAfter(10);

        int rows = (int) Math.ceil(items.size() / 2.0);
        for (int r = 0; r < rows; r++) {
            addBasicPair(grid, r < items.size() ? items.get(r) : null, labelFont, valueFont);
            int right = r + rows;
            addBasicPair(grid, right < items.size() ? items.get(right) : null, labelFont, valueFont);
        }
        document.add(grid);
    }

    /** 往网格里放一对「标签｜值」；kv 为 null 时补两个空格子，保持网格完整。 */
    private static void addBasicPair(PdfPTable grid, String[] kv, Font labelFont, Font valueFont) {
        PdfPCell label = new PdfPCell(new Phrase(kv == null ? "" : kv[0], labelFont));
        label.setBorder(Rectangle.NO_BORDER);
        label.setHorizontalAlignment(Element.ALIGN_RIGHT);
        label.setPaddingRight(8f);
        label.setPaddingBottom(7f);

        PdfPCell value = new PdfPCell(new Phrase(kv == null ? "" : kv[1], valueFont));
        value.setBorder(Rectangle.NO_BORDER);
        value.setPaddingRight(12f);
        value.setPaddingBottom(7f);

        grid.addCell(label);
        grid.addCell(value);
    }

    /**
     * 小节：标题左侧加一段品牌色竖条，正文缩进对齐到标题。
     *
     * 原先是「彩色标题 + 一条通栏细线」，通栏线会把版面切成一段一段，
     * 读起来像表单而不像简历；竖条只标记起点，段落之间靠间距分隔，更接近排版物。
     */
    private static void addSection(Document document, String title, String content,
                                   Font sectionFont, Font bodyFont, BaseColor barColor) throws DocumentException {
        if (content == null || content.trim().isEmpty()) return;

        addSectionHeader(document, title, sectionFont, barColor);

        Paragraph body = new Paragraph(content, bodyFont);
        applyCjkLineBreaking(body);
        body.setIndentationLeft(7f);
        body.setLeading(16f);
        document.add(body);
    }

    /**
     * 标签小节：技术栈、期望部门这类枚举值排成浅底标签，而不是逗号拼成一句话。
     * 逗号串在纸上很难扫，标签能一眼数清有几项。
     */
    private static void addChipSection(Document document, String title, String rawValue,
                                       Font sectionFont, Font chipFont, BaseColor barColor,
                                       BaseColor chipBg) throws DocumentException {
        String joined = joinIfJsonArray(rawValue);
        if (joined == null || joined.trim().isEmpty()) return;

        // 只按分隔符切，不按空白切：按 \s+ 切会把「Spring Boot」「Machine Learning」
        // 这类含空格的技术名拆成两个标签
        String[] parts = joined.split("[,，、;；\\n\\r]+");
        java.util.List<String> chips = new ArrayList<>();
        for (String part : parts) {
            String t = part.trim();
            if (!t.isEmpty()) chips.add(t);
        }
        if (chips.isEmpty()) return;
        if (chips.size() == 1) {
            // 只有一项时做成标签反而突兀，退回普通段落
            addSection(document, title, chips.get(0), sectionFont, chipFont, barColor);
            return;
        }

        addSectionHeader(document, title, sectionFont, barColor);

        // 按文字宽度分配列宽，末尾加一根「填充列」吸收剩余宽度。
        // 不这么做的话 PdfPTable 会把每格拉成等宽，标签连成一条灰带、看着像表格。
        BaseFont bf = chipFont.getBaseFont();
        float size = chipFont.getSize();
        float padding = 16f;

        int perRow = 5;
        for (int i = 0; i < chips.size(); i += perRow) {
            java.util.List<String> rowChips = chips.subList(i, Math.min(i + perRow, chips.size()));
            float[] widths = new float[rowChips.size() + 1];
            float used = 0f;
            for (int j = 0; j < rowChips.size(); j++) {
                float w = (bf != null ? bf.getWidthPoint(rowChips.get(j), size) : rowChips.get(j).length() * size * 0.6f)
                        + padding;
                widths[j] = w;
                used += w;
            }
            // 填充列：至少留一点，否则单行标签过宽时宽度数组会出现 0/负值
            widths[rowChips.size()] = Math.max(20f, 500f - used);

            PdfPTable row = new PdfPTable(widths);
            row.setWidthPercentage(100);
            row.setHorizontalAlignment(Element.ALIGN_LEFT);
            row.setSpacingAfter(4f);
            for (String chip : rowChips) {
                PdfPCell c = new PdfPCell(new Paragraph(chip, chipFont));
                c.setBackgroundColor(chipBg);
                c.setBorder(Rectangle.BOX);
                // 用与页面同色的边框做「间隙」：iText 的单元格之间没有 margin 概念
                c.setBorderColor(BaseColor.WHITE);
                c.setBorderWidth(2f);
                c.setPadding(4f);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                row.addCell(c);
            }
            PdfPCell filler = new PdfPCell(new Paragraph(" ", chipFont));
            filler.setBorder(Rectangle.NO_BORDER);
            row.addCell(filler);
            document.add(row);
        }
    }

    /** 小节标题：左侧品牌色竖条 + 标题，正文与之左对齐 */
    private static void addSectionHeader(Document document, String title,
                                         Font sectionFont, BaseColor barColor) throws DocumentException {
        PdfPTable head = new PdfPTable(new float[]{0.16f, 20f});
        head.setWidthPercentage(100);
        head.setSpacingBefore(16f);
        head.setSpacingAfter(6f);

        PdfPCell bar = new PdfPCell();
        bar.setBackgroundColor(barColor);
        bar.setBorder(Rectangle.NO_BORDER);
        bar.setFixedHeight(13f);
        head.addCell(bar);

        PdfPCell titleCell = new PdfPCell(new Paragraph(title, sectionFont));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingLeft(7f);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        head.addCell(titleCell);
        document.add(head);
    }

    /**
     * 每页底部的页脚：左侧社团署名，右侧「第 N 页」。
     *
     * 用 PageEvent 而不是在正文末尾 add 一段文字——后者只会出现在最后一页，
     * 且位置随内容浮动，不是真正的页脚。
     */
    private static class FooterPageEvent extends com.itextpdf.text.pdf.PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                Font f = getFont(8, Font.NORMAL, new BaseColor(150, 158, 170));
                com.itextpdf.text.pdf.PdfContentByte cb = writer.getDirectContent();
                float y = document.bottom() - 18f;

                com.itextpdf.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_LEFT,
                        new Phrase("博远信息技术社 · boyuan.club", f),
                        document.left(), y, 0);

                com.itextpdf.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_RIGHT,
                        new Phrase("第 " + writer.getPageNumber() + " 页", f),
                        document.right(), y, 0);
            } catch (Exception ignored) {
                // 页脚画失败不应让整份导出失败——正文才是主体
            }
        }
    }


    /**
     * 行首禁则：不允许这些标点被甩到下一行开头。
     *
     * iText 对中文是「见缝就断」，样例里就出现过整句排完、句号孤零零掉到
     * 下一行的情况。这里按中文排版惯例，禁止在「下一个字是收尾标点」的位置断行，
     * 断点自然往前挪一格，标点跟着前一个字走。
     */
    private static final String NO_LINE_START = "。，、；：？！）】》」』〕”’%…‰°,.;:?!)]}>";

    /** 行尾禁则：开引号/开括号不该留在行末。 */
    private static final String NO_LINE_END = "（【《「『〔“‘([{<";

    /**
     * 在 iText 默认断行规则之上叠一层中文禁则。
     *
     * 必须继承 DefaultSplitCharacter 而不是从零实现：IDENTITY_H 编码下
     * 传进来的 char[] 装的是**字形编号**不是 Unicode，得靠 getCurrentCharacter
     * 经 PdfChunk 换算回字符。自己直接读 cc[current] 比的是一堆无意义的数字，
     * 会把正文整段截掉（实测自我介绍、项目经验的第二行直接消失）。
     */
    private static final SplitCharacter CJK_SPLIT = new DefaultSplitCharacter() {
        @Override
        public boolean isSplitCharacter(int start, int current, int end, char[] cc, PdfChunk[] ck) {
            if (!super.isSplitCharacter(start, current, end, cc, ck)) {
                return false;
            }
            char c = getCurrentCharacter(current, cc, ck);
            if (NO_LINE_END.indexOf(c) >= 0) {
                return false;   // 开引号/开括号不该留在行末
            }
            // cc.length 这道保护不能省：越界读会被 iText 吞掉，
            // 表现是正文后半段整段消失（实测自我介绍、项目经验的第二行）
            if (current + 1 <= end && current + 1 < cc.length) {
                char next = getCurrentCharacter(current + 1, cc, ck);
                if (NO_LINE_START.indexOf(next) >= 0) {
                    return false;   // 在这断行会让收尾标点落到下一行开头
                }
            }
            return true;
        }
    };

    /** 给段落里的所有 Chunk 装上中文断行规则。 */
    private static void applyCjkLineBreaking(Paragraph p) {
        for (Chunk c : p.getChunks()) {
            c.setSplitCharacter(CJK_SPLIT);
        }
    }

    /** 社徽，缓存住——每页页脚也要用，重复解码没意义。 */
    private static volatile Image BRAND_LOGO;
    private static volatile boolean BRAND_LOGO_LOADED;

    /**
     * 读打包在 jar 里的社徽。刻意不走 URL：邮件模板可以引外链（收件人在线看），
     * 但 PDF 是要离线传阅、打印、存档的，图片必须自带。
     */
    private static Image loadBrandLogo() {
        if (BRAND_LOGO_LOADED) {
            return BRAND_LOGO;
        }
        synchronized (FONT_LOCK) {
            if (!BRAND_LOGO_LOADED) {
                try (java.io.InputStream in = PdfExportUtil.class
                        .getResourceAsStream("/branding/logo.png")) {
                    if (in != null) {
                        BRAND_LOGO = Image.getInstance(in.readAllBytes());
                    }
                } catch (Exception e) {
                    // 拿不到社徽不该让整份简历导不出来，缺就缺了
                    log.warn("社徽加载失败，PDF 将不带 logo: {}", e.getMessage());
                }
                BRAND_LOGO_LOADED = true;
            }
            return BRAND_LOGO;
        }
    }

    private static Font getFont(int size, int style, BaseColor color) {
        Font f = getFont(size, style);
        f.setColor(color);
        return f;
    }

    /**
     * 获取字体，支持中文显示
     * @param size 字体大小
     * @param style 字体样式
     * @return 字体对象
     */
    private static Font getFont(int size, int style) {
        // 首先尝试创建支持中文的字体
        BaseFont baseFont = getChineseBaseFont();
        if (baseFont != null) {
            return new Font(baseFont, size, style);
        }
        
        // 如果无法创建中文字体，则使用默认字体
        return new Font(Font.FontFamily.HELVETICA, size, style);
    }
    
    /**
     * 获取支持中文的BaseFont
     * @return BaseFont对象，如果无法创建则返回null
     */
    private static BaseFont getChineseBaseFont() {
        // 用单独的标志位而不是「CHINESE_BASE_FONT != null」判断是否解析过：
        // 解析失败时结果本来就是 null，拿 null 当「还没解析」会导致每取一次字号
        // 就重跑一遍枚举与探针，日志也跟着刷屏
        if (FONT_RESOLVED) {
            return CHINESE_BASE_FONT;
        }
        synchronized (FONT_LOCK) {
            if (!FONT_RESOLVED) {
                CHINESE_BASE_FONT = resolveChineseBaseFont();
                FONT_RESOLVED = true;
            }
            return CHINESE_BASE_FONT;
        }
    }

    /**
     * 挑一个真的能写简体中文的字体，并尽量把它嵌进 PDF。
     *
     * 之前这里有两个叠在一起的坑，「期望部门」的「门」字渲染异常就是它们的产物：
     *
     * 1) 用 .ttc 的 0 号子字体。TTC 是字体合集，0 号并不是「默认那个」：
     *    容器里 NotoSansCJK-Regular.ttc 的 0 号是**日文**子字体，
     *    macOS 上 Songti.ttc 的 0 号是 STSongti-SC-**Black**（超粗黑）。
     *    实测日文 CJK 字体连「简/历/项」都没有字形。
     * 2) NOT_EMBEDDED。不嵌入时 PDF 只记字体名，阅读器找不到就自行替换；
     *    而 IDENTITY_H 下写进去的是**原字体的字形编号**，拿到替换字体里查表
     *    就会错位成别的字——个别汉字变形、其余侥幸正常，正是用户看到的现象。
     *
     * 所以这里改成：按名字挑简体子字体 → 用探针字符验证真有字形 → 优先嵌入。
     * 有些系统字体（如 macOS Songti）授权禁止嵌入，那就退回不嵌入使用，
     * 总好过一路跌到 Helvetica 把整份简历的中文变成空白。
     */
    private static BaseFont resolveChineseBaseFont() {
        for (String path : FONT_FILE_CANDIDATES) {
            if (!new java.io.File(path).isFile()) {
                continue;
            }
            for (String spec : expandFontFile(path)) {
                BaseFont bf = tryCreate(spec, BaseFont.IDENTITY_H);
                if (bf != null) {
                    log.info("PDF 中文字体: {}", spec);
                    return bf;
                }
            }
        }
        // 兜底：iText 内置的中日韩字体，CMap 资源由 itext-asian 提供。
        // 它不可嵌入，但走的是 Adobe 标准 CJK 编码而非 IDENTITY_H，
        // 阅读器按标准替换即可正确显示，没有 Identity-H 那种字形错位问题。
        for (String name : new String[]{"STSong-Light", "STSongStd-Light"}) {
            BaseFont bf = tryCreate(name, "UniGB-UCS2-H");
            if (bf != null) {
                log.info("PDF 中文字体（内置）: {}", name);
                return bf;
            }
        }
        log.error("找不到任何可用的中文字体，PDF 导出中文将无法显示");
        return null;
    }

    /**
     * .ttc 展开成按简体优先排序的子字体列表；普通字体文件原样返回。
     */
    private static java.util.List<String> expandFontFile(String path) {
        if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".ttc")) {
            return java.util.Collections.singletonList(path);
        }
        String[] names;
        try {
            names = BaseFont.enumerateTTCNames(path);
        } catch (Exception e) {
            log.debug("枚举 {} 的子字体失败，退回 0 号: {}", path, e.getMessage());
            return java.util.Collections.singletonList(path + ",0");
        }
        java.util.List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            idx.add(i);
        }
        idx.sort(java.util.Comparator.comparingInt(i -> subfontPenalty(names[i])));
        java.util.List<String> specs = new ArrayList<>(idx.size());
        for (int i : idx) {
            specs.add(path + "," + i);
        }
        return specs;
    }

    /** 分数越小越优先：要简体、要 Regular、不要等宽。 */
    private static int subfontPenalty(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        int penalty = 0;
        // 简体标识（sc / gb）优先；日韩繁体明确靠后
        if (!(n.contains("sc") || n.contains("gb"))) {
            penalty += 100;
        }
        if (n.contains("jp") || n.contains("kr") || n.contains("tc") || n.contains("hk")) {
            penalty += 200;
        }
        // 正文要常规字重，Black/Bold/Light 都不合适
        if (!n.contains("regular")) {
            penalty += 10;
        }
        if (n.contains("black") || n.contains("heavy") || n.contains("light") || n.contains("thin")) {
            penalty += 20;
        }
        if (n.contains("mono")) {
            penalty += 50;
        }
        return penalty;
    }

    /**
     * 建字体：先试嵌入，被授权拒绝再试不嵌入；两种都要过字形探针。
     */
    private static BaseFont tryCreate(String spec, String encoding) {
        for (boolean embedded : new boolean[]{BaseFont.EMBEDDED, BaseFont.NOT_EMBEDDED}) {
            try {
                BaseFont bf = BaseFont.createFont(spec, encoding, embedded);
                if (!hasSimplifiedGlyphs(bf)) {
                    log.debug("跳过 {}：缺少简体字形", spec);
                    return null;   // 字形不全是字体本身的问题，换嵌入方式也没用
                }
                return bf;
            } catch (Exception e) {
                log.debug("创建字体 {}（嵌入={}）失败: {}", spec, embedded, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 探针字符全是**简体独有**的字，日文/繁体字体会在这里露馅。
     * 「门」就是用户报的那个字，留在第一位当回归哨兵。
     */
    private static boolean hasSimplifiedGlyphs(BaseFont bf) {
        for (char c : "门简历项荐".toCharArray()) {
            if (!bf.charExists(c)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取状态文本描述
     * @param status 状态码
     * @return 状态描述
     */
    private static String getStatusText(Integer status) {
        if (status == null) return "未知";
        
        switch (status) {
            case 1: return "草稿";
            case 2: return "已提交";
            case 3: return "评审中";
            case 4: return "通过";
            case 5: return "未通过";
            default: return "未知状态";
        }
    }
    
    /**
     * 检查字段值是否为Base64图片
     * @param fieldValue 字段值
     * @return 如果是Base64图片返回true
     */
    private static boolean isBase64Image(String fieldValue) {
        if (fieldValue == null || fieldValue.length() < 20) {
            return false;
        }
        return fieldValue.startsWith("data:image/") && fieldValue.contains("base64,");
    }
    
    /**
     * 将Base64图片字符串转换为Image对象
     * @param base64String Base64图片字符串
     * @return Image对象，如果转换失败返回null
     */
    private static Image createImageFromBase64(String base64String) {
        try {
            // 提取Base64数据部分（去掉data:image/jpeg;base64,前缀）
            String base64Data = base64String.substring(base64String.indexOf(",") + 1);
            
            // 解码Base64
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            
            // 创建Image对象
            Image image = Image.getInstance(imageBytes);
            
            // 设置图片大小为页面的1/6左右
            // A4页面宽度约595点，高度约842点
            float pageWidth = PageSize.A4.getWidth() - 80; // 减去左右边距
            float maxWidth = pageWidth / 6; // 页面宽度的1/6
            float maxHeight = maxWidth; // 保持正方形比例
            
            if (image.getWidth() > maxWidth || image.getHeight() > maxHeight) {
                image.scaleToFit(maxWidth, maxHeight);
            }
            
            System.out.println("成功解析Base64图片，原始尺寸: " + image.getPlainWidth() + "x" + image.getPlainHeight());
            return image;
            
        } catch (Exception e) {
            System.err.println("Base64图片转换失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 格式化日期时间
     * @param dateTime 日期时间
     * @return 格式化后的字符串
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}