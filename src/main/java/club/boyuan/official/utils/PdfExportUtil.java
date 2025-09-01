package club.boyuan.official.utils;

import club.boyuan.official.dto.ResumeDTO;
import club.boyuan.official.dto.ResumeFieldValueDTO;
import club.boyuan.official.dto.SimpleResumeFieldDTO;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF导出工具类
 * 用于将简历数据导出为PDF格式
 */
public class PdfExportUtil {
    
    /**
     * 将简历数据导出为PDF格式
     * @param resumeDTO 简历数据传输对象
     * @return PDF字节数组
     * @throws DocumentException 文档异常
     */
    public static byte[] exportResumeToPdf(ResumeDTO resumeDTO) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 创建文档
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();
        
        // 设置中文字体
        Font titleFont = getChineseFont(18, Font.BOLD);
        Font headerFont = getChineseFont(14, Font.BOLD);
        Font normalFont = getChineseFont(10, Font.NORMAL);
        
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
    }
    
    /**
     * 获取支持中文的字体
     * @param size 字体大小
     * @param style 字体样式
     * @return 支持中文的字体
     */
    private static Font getChineseFont(int size, int style) {
        try {
            // 尝试使用系统中文字体
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(baseFont, size, style);
        } catch (Exception e) {
            try {
                // 尝试使用系统默认中文字体
                BaseFont baseFont = BaseFont.createFont("C:/Windows/Fonts/simsun.ttc,0", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                return new Font(baseFont, size, style);
            } catch (Exception ex) {
                try {
                    // 尝试使用微软雅黑字体
                    BaseFont baseFont = BaseFont.createFont("C:/Windows/Fonts/msyh.ttc,0", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    return new Font(baseFont, size, style);
                } catch (Exception exc) {
                    try {
                        // 使用iText内置的通用字体
                        BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.EMBEDDED);
                        return new Font(baseFont, size, style);
                    } catch (Exception exce) {
                        // 最后回退到默认字体
                        return new Font(Font.FontFamily.HELVETICA, size, style);
                    }
                }
            }
        }
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