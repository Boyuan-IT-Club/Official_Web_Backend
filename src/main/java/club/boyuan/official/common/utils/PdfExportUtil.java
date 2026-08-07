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

            Document document = new Document(PageSize.A4, 48, 48, 44, 44);
            PdfWriter.getInstance(document, baos);
            document.open();

            BaseColor brand = new BaseColor(31, 58, 96);      // 深蓝
            BaseColor accent = new BaseColor(31, 118, 204);   // 品牌蓝
            BaseColor lightLine = new BaseColor(225, 232, 240);
            BaseColor subText = new BaseColor(110, 120, 135);

            Font bannerCn = getFont(20, Font.BOLD, BaseColor.WHITE);
            Font bannerSub = getFont(9, Font.NORMAL, new BaseColor(200, 215, 235));
            Font sectionFont = getFont(12, Font.BOLD, accent);
            Font labelFont = getFont(9, Font.NORMAL, subText);
            Font valueFont = getFont(11, Font.NORMAL, new BaseColor(35, 40, 48));
            Font bodyFont = getFont(10, Font.NORMAL, new BaseColor(55, 62, 72));
            Font footFont = getFont(8, Font.NORMAL, subText);

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

                // ── 顶部品牌横幅 ─────────────────────────────
                PdfPTable banner = new PdfPTable(1);
                banner.setWidthPercentage(100);
                PdfPCell bc = new PdfPCell();
                bc.setBackgroundColor(brand);
                bc.setBorder(Rectangle.NO_BORDER);
                bc.setPadding(16f);
                Paragraph bt = new Paragraph(name, bannerCn);
                Paragraph bs = new Paragraph("博远信息技术社 · 招新申请简历", bannerSub);
                bs.setSpacingBefore(4);
                bc.addElement(bt);
                bc.addElement(bs);
                banner.addCell(bc);
                banner.setSpacingAfter(14);
                document.add(banner);

                // ── 基本信息（左双列 + 右照片）───────────────
                PdfPTable info = new PdfPTable(photoImage != null ? new float[]{2.2f, 2.2f, 1.3f} : new float[]{1f, 1f});
                info.setWidthPercentage(100);
                info.setSpacingAfter(10);
                java.util.List<String[]> basics = new ArrayList<>();
                addBasic(basics, "学号", byKey.get("student_id"));
                addBasic(basics, "邮箱", byKey.get("email"));
                addBasic(basics, "手机", byKey.get("phone"));
                addBasic(basics, "年级", byKey.get("grade"));
                addBasic(basics, "性别", byKey.get("gender"));
                addBasic(basics, "专业", byKey.get("major"));
                addBasic(basics, "GitHub", byKey.get("github"));
                addBasic(basics, "期望部门", joinIfJsonArray(byKey.get("expected_departments")));

                int perCol = (int) Math.ceil(basics.size() / 2.0);
                PdfPCell colA = basicColumn(basics.subList(0, Math.min(perCol, basics.size())), labelFont, valueFont);
                PdfPCell colB = basicColumn(basics.size() > perCol ? basics.subList(perCol, basics.size()) : new ArrayList<>(), labelFont, valueFont);
                info.addCell(colA);
                info.addCell(colB);
                if (photoImage != null) {
                    PdfPCell pc = new PdfPCell(photoImage, true);
                    pc.setBorder(Rectangle.NO_BORDER);
                    pc.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    pc.setPadding(2f);
                    info.addCell(pc);
                }
                document.add(info);

                // ── 长文本小节 ──────────────────────────────
                addSection(document, "技术栈", joinIfJsonArray(byKey.get("tech_stack")), sectionFont, bodyFont, lightLine);
                addSection(document, "个人简介", firstNonBlank(byKey.get("self_introduction"), byKey.get("introduction")), sectionFont, bodyFont, lightLine);
                addSection(document, "项目经验", byKey.get("project_experience"), sectionFont, bodyFont, lightLine);
                addSection(document, "加入原因", byKey.get("reason"), sectionFont, bodyFont, lightLine);

                // 其余未归类字段（模板可扩展，逐条列出）
                java.util.Set<String> known = new java.util.HashSet<>(java.util.Arrays.asList(
                        "name", "student_id", "email", "phone", "grade", "gender", "major", "github",
                        "expected_departments", "tech_stack", "self_introduction", "introduction",
                        "project_experience", "reason", "personal_photo"));
                StringBuilder extras = new StringBuilder();
                for (SimpleResumeFieldDTO f : fields) {
                    if (f.getFieldKey() == null || known.contains(f.getFieldKey())) continue;
                    if (f.getFieldValue() == null || f.getFieldValue().trim().isEmpty()) continue;
                    if (isBase64Image(f.getFieldValue())) continue;
                    if (extras.length() > 0) extras.append("\n");
                    extras.append(f.getFieldLabel() != null ? f.getFieldLabel() : f.getFieldKey())
                          .append("：").append(joinIfJsonArray(f.getFieldValue()));
                }
                if (extras.length() > 0) {
                    addSection(document, "其他信息", extras.toString(), sectionFont, bodyFont, lightLine);
                }

                // ── 页脚 ────────────────────────────────────
                Paragraph foot = new Paragraph(
                        "状态：" + getStatusText(resumeDTO.getStatus())
                                + "    导出时间：" + formatDateTime(LocalDateTime.now())
                                + "    boyuan.club",
                        footFont);
                foot.setAlignment(Element.ALIGN_RIGHT);
                foot.setSpacingBefore(24);
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
        if (value != null && !value.trim().isEmpty()) list.add(new String[]{label, value});
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

    private static void addSection(Document document, String title, String content,
                                   Font sectionFont, Font bodyFont, BaseColor lineColor) throws DocumentException {
        if (content == null || content.trim().isEmpty()) return;
        Paragraph st = new Paragraph(title, sectionFont);
        st.setSpacingBefore(14);
        st.setSpacingAfter(2);
        document.add(st);
        com.itextpdf.text.pdf.draw.LineSeparator sep = new com.itextpdf.text.pdf.draw.LineSeparator(0.8f, 100, lineColor, Element.ALIGN_LEFT, 2);
        document.add(sep);
        Paragraph body = new Paragraph(content, bodyFont);
        body.setSpacingBefore(8);
        body.setLeading(16f);
        document.add(body);
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