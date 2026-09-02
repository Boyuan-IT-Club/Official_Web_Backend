package club.boyuan.official.common.utils;

import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.ResumeFieldValueDTO;
import club.boyuan.official.domain.resume.dto.SimpleResumeFieldDTO;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
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

/**
 * PDF导出工具类
 * 用于将简历数据导出为PDF格式
 */
public class PdfExportUtil {
    
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
                PdfPTable info = new PdfPTable(new float[]{1f, 1f});
                info.setWidthPercentage(100);
                info.setSpacingAfter(10);
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

                int perCol = (int) Math.ceil(basics.size() / 2.0);
                PdfPCell colA = basicColumn(basics.subList(0, Math.min(perCol, basics.size())), labelFont, valueFont);
                PdfPCell colB = basicColumn(basics.size() > perCol ? basics.subList(perCol, basics.size()) : new ArrayList<>(), labelFont, valueFont);
                info.addCell(colA);
                info.addCell(colB);
                document.add(info);

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

    private static PdfPCell basicColumn(java.util.List<String[]> items, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingRight(10f);
        for (String[] kv : items) {
            Paragraph line = new Paragraph();
            line.setSpacingAfter(6);
            line.add(new Chunk(kv[0] + "  ", labelFont));
            line.add(new Chunk(kv[1], valueFont));
            cell.addElement(line);
        }
        return cell;
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
        // 优先尝试Docker容器中常见的字体路径
        String[][] fontConfigs = {
            // 容器中的Noto字体 - 按实际存在的路径优先排序
            {"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/truetype/noto/NotoSerifCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            
            // 单独的CJK字体文件
            {"/usr/share/fonts/opentype/noto/NotoSansCJK-SC-Regular.otf", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/opentype/noto/NotoSerifCJK-SC-Regular.otf", BaseFont.IDENTITY_H},
            
            // 容器中的DejaVu字体
            {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf", BaseFont.IDENTITY_H},
            
            // iText内置中文字体
            {"STSong-Light", "UniGB-UCS2-H"},
            {"STSongStd-Light", "UniGB-UCS2-H"},
            
            // Windows系统字体（本地开发环境）
            {"C:/Windows/Fonts/simsun.ttc,0", BaseFont.IDENTITY_H},
            {"C:/Windows/Fonts/msyh.ttc,0", BaseFont.IDENTITY_H},

            // macOS 系统字体（本地开发环境）。
            // 原先列表里有 Linux 与 Windows 却没有 macOS，导致 Mac 上导出会一路回退到
            // Helvetica——那个字体没有中文字形，整份简历的中文全变空白，
            // 而这在生产（Linux 容器有 Noto CJK）看不出来，只有本地验证版式时才撞上。
            {"/System/Library/Fonts/Supplemental/Songti.ttc,0", BaseFont.IDENTITY_H},
            {"/System/Library/Fonts/Hiragino Sans GB.ttc,0", BaseFont.IDENTITY_H},
            {"/System/Library/Fonts/STHeiti Medium.ttc,0", BaseFont.IDENTITY_H},
            
            // 备用选项 - 使用默认字体
            {BaseFont.HELVETICA, BaseFont.CP1252}
        };
        
        for (String[] fontConfig : fontConfigs) {
            try {
                BaseFont baseFont = BaseFont.createFont(fontConfig[0], fontConfig[1], BaseFont.NOT_EMBEDDED);
                // 成功创建字体，记录日志并返回
                System.out.println("PDF字体初始化成功: " + fontConfig[0]);
                return baseFont;
            } catch (Exception e) {
                // 忽略异常，尝试下一个字体配置
                System.out.println("PDF字体初始化失败: " + fontConfig[0] + ", 错误: " + e.getMessage());
            }
        }
        
        // 所有字体都失败，记录警告并返回null
        System.err.println("警告: 所有PDF字体初始化尝试都失败，将使用默认字体");
        return null;
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