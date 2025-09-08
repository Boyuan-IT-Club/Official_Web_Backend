package club.boyuan.official.utils;

import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel导出工具类
 * 用于将用户数据导出为Excel格式
 */
public class ExcelExportUtil {
    
    /**
     * 将用户列表导出为Excel格式
     * @param users 用户列表
     * @return Excel字节数组
     * @throws BusinessException 导出失败时抛出业务异常
     */
    public static byte[] exportUsersToExcel(List<User> users) throws BusinessException {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("用户列表");
            
            // 创建标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // 创建数据行样式
            CellStyle dataStyle = workbook.createCellStyle();
            
            try {
                // 创建标题行
                Row headerRow = sheet.createRow(0);
                String[] headers = {"用户ID", "用户名", "姓名", "邮箱", "手机号", "专业", "GitHub", "部门", "角色", "状态", "会员状态", "创建时间"};
                
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }
                
                // 填充数据
                int rowNum = 1;
                for (User user : users) {
                    Row row = sheet.createRow(rowNum++);
                    
                    row.createCell(0).setCellValue(user.getUserId() != null ? user.getUserId() : 0);
                    row.createCell(1).setCellValue(user.getUsername() != null ? user.getUsername() : "");
                    row.createCell(2).setCellValue(user.getName() != null ? user.getName() : "");
                    row.createCell(3).setCellValue(user.getEmail() != null ? user.getEmail() : "");
                    row.createCell(4).setCellValue(user.getPhone() != null ? user.getPhone() : "");
                    row.createCell(5).setCellValue(user.getMajor() != null ? user.getMajor() : "");
                    row.createCell(6).setCellValue(user.getGithub() != null ? user.getGithub() : "");
                    row.createCell(7).setCellValue(user.getDept() != null ? user.getDept() : "");
                    row.createCell(8).setCellValue(user.getRole() != null ? user.getRole() : "");
                    row.createCell(9).setCellValue(user.getStatus() != null ? (user.getStatus() ? "激活" : "冻结") : "未知");
                    row.createCell(10).setCellValue(user.getIsMember() != null ? (user.getIsMember() ? "是" : "否") : "未知");
                    row.createCell(11).setCellValue(user.getCreateTime() != null ? 
                        user.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
                }
                
                // 自动调整列宽
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
                
                // 将工作簿写入字节数组
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                return baos.toByteArray();
                
            } finally {
                workbook.close();
            }
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.EXPORT_EXCEL_FAILED);
        }
    }
}