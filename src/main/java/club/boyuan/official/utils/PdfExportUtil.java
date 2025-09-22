package club.boyuan.official.utils;

import club.boyuan.official.dto.ResumeDTO;
import club.boyuan.official.dto.ResumeFieldValueDTO;
import club.boyuan.official.dto.SimpleResumeFieldDTO;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 创建文档
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();
            
            // 设置字体
            Font titleFont = getFont(18, Font.BOLD);
            Font headerFont = getFont(14, Font.BOLD);
            Font normalFont = getFont(10, Font.NORMAL);
            
            try {
                // 添加标题
                Paragraph title = new Paragraph("个人简历", titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                title.setSpacingAfter(20);
                document.add(title);
                
                // 添加基本信息
                PdfPTable infoTable = new PdfPTable(2);
                infoTable.setWidthPercentage(100);
                infoTable.setSpacingAfter(20);
                
                PdfPCell cell1 = new PdfPCell(new Paragraph("用户ID: " + resumeDTO.getUserId(), normalFont));
                PdfPCell cell2 = new PdfPCell(new Paragraph("招募周期ID: " + resumeDTO.getCycleId(), normalFont));
                PdfPCell cell3 = new PdfPCell(new Paragraph("状态: " + getStatusText(resumeDTO.getStatus()), normalFont));
                PdfPCell cell4 = new PdfPCell(new Paragraph("创建时间: " + formatDateTime(resumeDTO.getCreatedAt()), normalFont));
                
                infoTable.addCell(cell1);
                infoTable.addCell(cell2);
                infoTable.addCell(cell3);
                infoTable.addCell(cell4);
                
                document.add(infoTable);
                
                // 添加字段值信息
                if (resumeDTO.getSimpleFields() != null && !resumeDTO.getSimpleFields().isEmpty()) {
                    Paragraph fieldsTitle = new Paragraph("简历详情", headerFont);
                    fieldsTitle.setSpacingBefore(20);
                    fieldsTitle.setSpacingAfter(10);
                    document.add(fieldsTitle);
                    
                    PdfPTable fieldsTable = new PdfPTable(2);
                    fieldsTable.setWidthPercentage(100);
                    fieldsTable.setWidths(new int[]{1, 3});
                    
                    // 表头
                    PdfPCell header1 = new PdfPCell(new Paragraph("字段名称", headerFont));
                    PdfPCell header2 = new PdfPCell(new Paragraph("字段值", headerFont));
                    fieldsTable.addCell(header1);
                    fieldsTable.addCell(header2);
                    
                    // 数据行
                    for (SimpleResumeFieldDTO field : resumeDTO.getSimpleFields()) {
                        PdfPCell fieldLabel = new PdfPCell(new Paragraph(
                            field.getFieldLabel() != null ? field.getFieldLabel() : "未知字段", 
                            normalFont));
                        PdfPCell fieldValueCell = new PdfPCell(new Paragraph(
                            field.getFieldValue() != null ? field.getFieldValue() : "", 
                            normalFont));
                        fieldsTable.addCell(fieldLabel);
                        fieldsTable.addCell(fieldValueCell);
                    }
                    
                    document.add(fieldsTable);
                }
                
                // 添加导出时间
                Paragraph exportTime = new Paragraph("导出时间: " + formatDateTime(LocalDateTime.now()), normalFont);
                exportTime.setAlignment(Element.ALIGN_RIGHT);
                exportTime.setSpacingBefore(20);
                document.add(exportTime);
                
            } finally {
                document.close();
            }
            
            return baos.toByteArray();
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
            // 容器中的Noto字体 - 最高优先级
            {"/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/truetype/noto/NotoSerifCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            {"/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc,0", BaseFont.IDENTITY_H},
            
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
     * 格式化日期时间
     * @param dateTime 日期时间
     * @return 格式化后的字符串
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}