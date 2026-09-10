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
            //
            // PingFang 在新版 macOS 上已不在这个路径（本机实测落到了宋体，
            // 导出的样张是衬线体，和线上完全两个观感）。补上 Hiragino Sans GB 兜底。
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/Hiragino Sans GB.ttc",
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
        return exportResumeToPdf(resumeDTO, null);
    }

    /**
     * @param photoBytes 照片字节。personal_photo 字段值迁到 COS 后存的是 objectKey，
     *                   本类拿不到存储服务（静态工具类），由调用方先取回字节传入；
     *                   传 null 则退回旧逻辑——扫字段里的 base64 data URL。
     */
    public static byte[] exportResumeToPdf(ResumeDTO dto, byte[] photoBytes) throws BusinessException {
        try {
            Content c = readContent(dto, photoBytes);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 52, 52, 44, 52);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterPageEvent());
            doc.open();

            BaseColor accent = new BaseColor(31, 118, 204);
            BaseColor sub = new BaseColor(110, 120, 135);
            BaseColor ink = new BaseColor(35, 40, 48);
            Font bodyFont = getFont(10, Font.NORMAL, new BaseColor(55, 62, 72));
            Font sectionFont = getFont(11, Font.BOLD, accent);
            Font chipFont = getFont(9, Font.NORMAL, new BaseColor(31, 58, 96));
            BaseColor chipBg = new BaseColor(234, 239, 247);

            // ── 抬头：姓名左对齐，照片贴右上；两行灰色小字交代身份与联系方式 ──
            // 密度是这一版的重点：三行就把「你是谁、怎么找到你」说完，
            // 剩下的纵向空间全留给正文。
            PdfPTable head = new PdfPTable(c.photo != null ? new float[]{5.2f, 1f} : new float[]{1f});
            head.setWidthPercentage(100);
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.setPaddingLeft(0);
            Paragraph nm = new Paragraph(c.name, getFont(23, Font.BOLD, ink));
            nm.setSpacingAfter(3);
            left.addElement(nm);
            String idLine = joinNonBlank(" · ", c.get("major"), c.get("grade"), c.get("student_id"), c.get("gender"));
            if (!idLine.isBlank()) {
                left.addElement(new Paragraph(idLine, getFont(9, Font.NORMAL, sub)));
            }
            String contactLine = joinNonBlank("   |   ", c.get("phone"), c.get("email"), c.get("github"));
            if (!contactLine.isBlank()) {
                Paragraph ct = new Paragraph(contactLine, getFont(9, Font.NORMAL, sub));
                ct.setSpacingBefore(2);
                left.addElement(ct);
            }
            head.addCell(left);
            if (c.photo != null) {
                PdfPCell ph = new PdfPCell(c.photo, true);
                ph.setBorder(Rectangle.NO_BORDER);
                ph.setHorizontalAlignment(Element.ALIGN_RIGHT);
                ph.setFixedHeight(62f);
                head.addCell(ph);
            }
            head.setSpacingAfter(9);
            doc.add(head);
            doc.add(hairline(accent, 1.4f, 10f));

            addFlatSection(doc, c, "expected_departments", "期望部门", true, sectionFont, bodyFont, chipFont, chipBg, accent);
            addFlatSection(doc, c, "tech_stack", "技术栈", true, sectionFont, bodyFont, chipFont, chipBg, accent);
            addFlatSection(doc, c, "self_introduction", "自我介绍", false, sectionFont, bodyFont, chipFont, chipBg, accent);
            addFlatSection(doc, c, "reason", "加入原因", false, sectionFont, bodyFont, chipFont, chipBg, accent);
            addFlatSection(doc, c, "introduction", "个人简介", false, sectionFont, bodyFont, chipFont, chipBg, accent);
            addFlatSection(doc, c, "project_experience", "项目经验", false, sectionFont, bodyFont, chipFont, chipBg, accent);
            String extras = extraFields(c);
            if (!extras.isBlank()) {
                addLeftSectionHeader(doc, "其他信息", sectionFont, accent);
                doc.add(indented(extras, bodyFont));
            }

            doc.close();
            return baos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.EXPORT_PDF_FAILED, "PDF导出失败: " + e.getMessage());
        }
    }

    /**
     * 空白报名表模板的一项。
     *
     * 用本地 record 而不是直接收 ResumeFieldDefinition：这个工具类在 common 层，
     * 不该反过来依赖持久层实体。调用方（ResumeController）负责翻译。
     */
    public record TemplateField(String label, String type, boolean required,
                                java.util.List<String> options, String placeholder) {
    }

    /**
     * 渲染一份空白报名表，供周期还没开始时提前准备内容用。
     *
     * 为什么不复用 exportResumeToPdf 塞一份空简历：那条路上长文本小节遇到空值
     * 会整节跳过（对已填的简历是对的——没写就别占版面），空模板走一遍只剩
     * 一张基本信息表，看不出要写什么。这里给每一项都留出书写区，
     * 并把填写方式与选项标出来，才是「模板」该有的样子。
     *
     * 版式与简历导出同一套：同样的页眉、品牌线、小节竖条与页脚，
     * 打印出来和正式简历是一家人。
     */
    public static byte[] exportBlankTemplateToPdf(String cycleName, java.util.List<TemplateField> fields)
            throws BusinessException {
        try {
            getChineseBaseFont();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 52, 52, 44, 52);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new FooterPageEvent());
            document.open();

            BaseColor accent = new BaseColor(31, 118, 204);
            BaseColor subText = new BaseColor(110, 120, 135);
            BaseColor blankBorder = new BaseColor(205, 214, 226);
            Font sectionFont = getFont(12, Font.BOLD, accent);
            Font hintFont = getFont(9, Font.NORMAL, subText);
            Font footFont = getFont(8, Font.NORMAL, subText);
            String title = (cycleName == null || cycleName.isBlank()) ? "招新报名表" : cycleName.trim();

            try {
                // ── 抬头：与简历导出同构（左对齐标题 + 灰色副标题 + 品牌色通栏线）──
                Paragraph h = new Paragraph(title, getFont(21, Font.BOLD, new BaseColor(28, 33, 42)));
                h.setSpacingAfter(3f);
                document.add(h);
                Paragraph subtitle = new Paragraph("博远信息技术社 · 招新报名表（空白模板）",
                        getFont(9, Font.NORMAL, subText));
                subtitle.setSpacingAfter(6f);
                document.add(subtitle);
                document.add(hairline(accent, 1.4f, 10f));

                Paragraph lead = new Paragraph(
                        "本表仅供提前准备内容，不能代替在线报名；招募开放后请到官网填写并提交。带 * 的为必填项。",
                        hintFont);
                lead.setSpacingAfter(4);
                document.add(lead);

                for (TemplateField f : fields) {
                    if (f == null || f.label() == null || f.label().isBlank()) continue;
                    addSectionHeader(document, f.label() + (f.required() ? " *" : ""), sectionFont, accent);

                    String hint = typeHint(f);
                    if (f.placeholder() != null && !f.placeholder().isBlank()) {
                        hint = hint + "｜" + f.placeholder().trim();
                    }
                    Paragraph hp = new Paragraph(hint, hintFont);
                    hp.setIndentationLeft(7f);
                    hp.setSpacingAfter(4f);
                    document.add(hp);

                    // 书写区：多行题给足高度，单行题一行就够——照着框的大小
                    // 就能估出该写多少，这是纸质表单最有用的一点提示
                    document.add(blankBox(isLongText(f.type()) ? 74f : 26f, blankBorder));
                }

                Paragraph foot = new Paragraph("导出时间：" + formatDateTime(LocalDateTime.now()), footFont);
                foot.setAlignment(Element.ALIGN_RIGHT);
                foot.setSpacingBefore(20);
                document.add(foot);
            } finally {
                document.close();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("空白报名表导出失败 cycleName={}", cycleName, e);
            throw new BusinessException(BusinessExceptionEnum.EXPORT_PDF_FAILED,
                    "模板导出失败: " + e.getMessage());
        }
    }

    private static boolean isLongText(String type) {
        return "textarea".equals(type) || "project_experience".equals(type);
    }

    /** 「怎么填」的一句话说明；选项类把选项列出来，免得对着空框猜 */
    private static String typeHint(TemplateField f) {
        java.util.List<String> opts = f.options() == null ? List.of() : f.options();
        String joined = String.join(" / ", opts);
        return switch (f.type() == null ? "" : f.type()) {
            case "textarea" -> "多行文本";
            case "radio" -> opts.isEmpty() ? "单选" : "单选：" + joined;
            case "checkbox" -> opts.isEmpty() ? "多选" : "多选：" + joined;
            case "select" -> opts.isEmpty() ? "下拉选择" : "下拉选择：" + joined;
            case "photo", "image" -> "上传图片（在线填写时上传）";
            case "file" -> "上传附件（在线填写时上传）";
            case "date" -> "选择日期";
            default -> "单行文本";
        };
    }

    /** 一个留白书写框。用浅色细边而不是下划线：多行题下划线画不出高度 */
    private static PdfPTable blankBox(float height, BaseColor border) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(height);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setBorderWidth(0.7f);
        box.addCell(cell);
        box.setSpacingAfter(2f);
        return box;
    }

    // ── 版式零件 ────────────────────────────────────────────────

    /**
     * 切标签。只按分隔符切，不按空白切：按 \s+ 切会把「Spring Boot」
     * 「Machine Learning」这类含空格的技术名拆成两个标签。
     */
    private static java.util.List<String> splitChips(String joined) {
        java.util.List<String> chips = new ArrayList<>();
        for (String part : joined.split("[,，、;；\\n\\r]+")) {
            String t = part.trim();
            if (!t.isEmpty()) chips.add(t);
        }
        return chips;
    }

    /** 左对齐标题版的标签小节；只有一项时退回普通段落，做成标签反而突兀 */
    private static void addLeftChipSection(Document doc, String title, String rawValue,
                                           Font sectionFont, Font chipFont, Font bodyFont,
                                           BaseColor accent, BaseColor chipBg) throws DocumentException {
        String joined = joinIfJsonArray(rawValue);
        if (joined == null || joined.isBlank()) return;
        java.util.List<String> chips = splitChips(joined);
        if (chips.isEmpty()) return;
        addLeftSectionHeader(doc, title, sectionFont, accent);
        if (chips.size() == 1) {
            doc.add(indented(chips.get(0), bodyFont));
        } else {
            addChips(doc, chips, chipFont, chipBg);
        }
    }

    /** 一份简历读一次就够的东西：字段值、标签覆盖、照片、姓名 */
    private static final class Content {
        java.util.Map<String, String> byKey = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> labelOf = new java.util.LinkedHashMap<>();
        java.util.List<SimpleResumeFieldDTO> fields = new ArrayList<>();
        Image photo;
        String name;

        String get(String key) {
            String v = byKey.get(key);
            return v == null ? "" : v;
        }

        String label(String key, String fallback) {
            return labelOf.getOrDefault(key, fallback);
        }
    }

    private static Content readContent(ResumeDTO dto, byte[] photoBytes) throws BusinessException {
        if (dto == null) {
            throw new BusinessException(BusinessExceptionEnum.EXPORT_PDF_FAILED, "PDF导出失败: 简历数据为空");
        }
        getChineseBaseFont();
        Content c = new Content();
        c.fields = dto.getSimpleFields() != null ? dto.getSimpleFields() : new ArrayList<>();
        for (SimpleResumeFieldDTO f : c.fields) {
            if (f.getFieldKey() != null) {
                c.byKey.put(f.getFieldKey(), f.getFieldValue());
                if (f.getFieldLabel() != null && !f.getFieldLabel().trim().isEmpty()) {
                    c.labelOf.put(f.getFieldKey(), f.getFieldLabel().trim());
                }
            }
        }
        c.photo = createImageFromBytes(photoBytes);
        if (c.photo == null) {
            for (SimpleResumeFieldDTO f : c.fields) {
                if (isBase64Image(f.getFieldValue())) {
                    c.photo = createImageFromBase64(f.getFieldValue());
                    break;
                }
            }
        }
        c.name = firstNonBlank(c.get("name"), "未填写姓名");
        return c;
    }

    /** 用分隔符连起非空片段；全空返回空串 */
    private static String joinNonBlank(String sep, String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(v -> v != null && !v.isBlank())
                .collect(java.util.stream.Collectors.joining(sep));
    }

    /** 通栏细线 */
    private static PdfPTable hairline(BaseColor color, float width, float spacingAfter) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(color);
        c.setBorderWidthBottom(width);
        c.setFixedHeight(1f);
        t.addCell(c);
        t.setSpacingAfter(spacingAfter);
        return t;
    }

    /** 左对齐小节标题 + 紧贴的通栏细线 */
    private static void addLeftSectionHeader(Document doc, String title, Font f, BaseColor line)
            throws DocumentException {
        Paragraph p = new Paragraph(title, f);
        p.setSpacingBefore(13f);
        p.setSpacingAfter(2f);
        doc.add(p);
        doc.add(hairline(new BaseColor(210, 222, 238), 0.8f, 6f));
    }

    private static Paragraph indented(String text, Font f) {
        Paragraph p = new Paragraph(text, f);
        applyCjkLineBreaking(p);
        p.setLeading(15f);
        return p;
    }

    /** 单栏版的一节：chips 与整段文字两种呈现 */
    private static void addFlatSection(Document doc, Content c, String key, String fallbackTitle,
                                       boolean asChips, Font sectionFont, Font bodyFont,
                                       Font chipFont, BaseColor chipBg, BaseColor accent)
            throws DocumentException {
        String raw = c.get(key);
        if (raw == null || raw.isBlank()) return;
        String title = c.label(key, fallbackTitle);
        if (asChips) {
            // 走左对齐标题：addChipSection 里调的是居中版标题，
            // 和这一版其余小节混在一起会一半居中一半靠左（第一张样张就是这样）
            addLeftChipSection(doc, title, raw, sectionFont, chipFont, bodyFont, accent, chipBg);
        } else {
            addLeftSectionHeader(doc, title, sectionFont, accent);
            doc.add(indented(raw, bodyFont));
        }
    }

    /** 管理员自定义字段拼成一段；标准字段已各自成节 */
    private static String extraFields(Content c) {
        java.util.Set<String> known = new java.util.HashSet<>(java.util.Arrays.asList(
                "name", "student_id", "gender", "grade", "major", "email", "phone", "github",
                "personal_photo", "photo", "self_introduction", "reason", "introduction",
                "first_choice", "second_choice", "expected_departments",
                "tech_stack", "project_experience",
                "can_attend_offline_interview", "expected_interview_time", "second_interview_time"));
        java.util.List<SimpleResumeFieldDTO> ordered = new ArrayList<>(c.fields);
        ordered.sort(java.util.Comparator.comparing(
                f -> f.getSortOrder() == null ? Integer.MAX_VALUE : f.getSortOrder()));
        StringBuilder sb = new StringBuilder();
        for (SimpleResumeFieldDTO f : ordered) {
            if (f.getFieldKey() == null || known.contains(f.getFieldKey())) continue;
            if (f.getFieldValue() == null || f.getFieldValue().trim().isEmpty()) continue;
            if (isBase64Image(f.getFieldValue())) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(f.getFieldLabel() != null ? f.getFieldLabel() : f.getFieldKey())
              .append("：").append(joinIfJsonArray(f.getFieldValue()));
        }
        return sb.toString();
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
     * 小节标题。版式几经反复：「彩色标题 + 通栏线」→「左侧品牌色竖条」→
     * 「居中 + 通栏线」，最后定在左对齐 + 通栏细线（社团选的经典单栏那一版）。
     * 保留这个名字是因为 addSection / addChipSection 都在调它。
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

        java.util.List<String> chips = splitChips(joined);
        if (chips.isEmpty()) return;
        if (chips.size() == 1) {
            // 只有一项时做成标签反而突兀，退回普通段落
            addSection(document, title, chips.get(0), sectionFont, chipFont, barColor);
            return;
        }

        addSectionHeader(document, title, sectionFont, barColor);
        addChips(document, chips, chipFont, chipBg);
    }

    /** 把已经切好的标签排成若干行。抽出来是为了让左对齐标题的版式也能复用 */
    private static void addChips(Document document, java.util.List<String> chips,
                                 Font chipFont, BaseColor chipBg) throws DocumentException {
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
        addLeftSectionHeader(document, title, sectionFont, barColor);
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
            // 首页的上边距是给弧形页眉与头像留的，第二页起收回正常值。
            // setMargins 对「下一页」生效，所以放在第一页结束时调。
            if (writer.getPageNumber() == 1) {
                document.setMargins(48, 48, 44, 52);
            }
            try {
                Font f = getFont(8, Font.NORMAL, new BaseColor(150, 158, 170));
                com.itextpdf.text.pdf.PdfContentByte cb = writer.getDirectContent();
                float y = document.bottom() - 18f;

                // 加一点内缩：双栏版式左右边距为 0，直接用 left()/right() 会贴到纸边
                float inset = 28f;
                com.itextpdf.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_LEFT,
                        new Phrase("博远信息技术社 · boyuan.club", f),
                        document.left() + (document.left() < 20f ? inset : 0f), y, 0);

                float rightEdge = document.getPageSize().getWidth() - document.rightMargin();
                com.itextpdf.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_RIGHT,
                        new Phrase("第 " + writer.getPageNumber() + " 页", f),
                        rightEdge - (document.rightMargin() < 20f ? inset : 0f), y, 0);
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
            /*
             * ck 里可能有 null：段落被放进表格单元格（addElement）时，iText 组装
             * chunk 数组的路径和直接 document.add 不一样，会留空位。
             * getCurrentCharacter 对它直接解引用，于是整份导出以 NPE 收场
             * （双栏版式的样张第一次渲染就是这么崩的）。
             *
             * 拿不到字符就退回默认断行：禁则是锦上添花，不值得为它把渲染搞崩。
             */
            if (!resolvable(current, cc, ck)) {
                return true;
            }
            char c = getCurrentCharacter(current, cc, ck);
            if (NO_LINE_END.indexOf(c) >= 0) {
                return false;   // 开引号/开括号不该留在行末
            }
            // cc.length 这道保护不能省：越界读会被 iText 吞掉，
            // 表现是正文后半段整段消失（实测自我介绍、项目经验的第二行）
            if (current + 1 <= end && current + 1 < cc.length && resolvable(current + 1, cc, ck)) {
                char next = getCurrentCharacter(current + 1, cc, ck);
                if (NO_LINE_START.indexOf(next) >= 0) {
                    return false;   // 在这断行会让收尾标点落到下一行开头
                }
            }
            return true;
        }

        /** 这个位置能不能换算回 Unicode——不能就别调 getCurrentCharacter */
        private boolean resolvable(int idx, char[] cc, PdfChunk[] ck) {
            return cc != null && idx >= 0 && idx < cc.length
                    && ck != null && ck.length > 0
                    && ck[Math.min(idx, ck.length - 1)] != null;
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

            return createImageFromBytes(imageBytes);

        } catch (Exception e) {
            System.err.println("Base64图片转换失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 由原始字节构造照片 Image（COS 迁移后照片以字节传入）。
     * @return Image对象，字节为空或解析失败返回null
     */
    private static Image createImageFromBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
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
            
            System.out.println("成功解析照片，原始尺寸: " + image.getPlainWidth() + "x" + image.getPlainHeight());
            return image;

        } catch (Exception e) {
            System.err.println("照片解析失败: " + e.getMessage());
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