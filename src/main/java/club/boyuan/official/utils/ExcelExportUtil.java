package club.boyuan.official.utils;

import club.boyuan.official.dto.InterviewAssignmentResultDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

    /**
     * 将面试安排结果导出为Excel，在同一个工作表中按部门区分
     * @param result 面试安排结果
     * @return Excel字节数组
     */
    public static byte[] exportInterviewAssignmentsToExcel(InterviewAssignmentResultDTO result) throws BusinessException {
        try {
            Workbook workbook = new XSSFWorkbook();

            // 通用样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeRangeFormat = DateTimeFormatter.ofPattern("HH:mm");

            // Sheet1: 已分配
            Sheet assignedSheet = workbook.createSheet("已分配");
            Row assignedHeader = assignedSheet.createRow(0);
            // 修改表头，添加部门列，删除用户ID列
            String[] assignedHeaders = {"用户名", "姓名", "所属部门", "面试时间", "时间段"};
            for (int i = 0; i < assignedHeaders.length; i++) {
                Cell cell = assignedHeader.createCell(i);
                cell.setCellValue(assignedHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int assignedRowNum = 1;
            if (result != null && result.getAssignedInterviews() != null) {
                // 按部门和时间排序
                List<InterviewAssignmentResultDTO.AssignedInterviewDTO> sortedAssigned = result.getAssignedInterviews()
                        .stream()
                        .sorted((a, b) -> {
                            // 首先按部门排序
                            int deptCompare = a.getInterviewDepartment().compareTo(b.getInterviewDepartment());
                            if (deptCompare != 0) {
                                return deptCompare;
                            }
                            // 然后按时间排序
                            return a.getInterviewTime().compareTo(b.getInterviewTime());
                        })
                        .collect(Collectors.toList());
                
                for (InterviewAssignmentResultDTO.AssignedInterviewDTO dto : sortedAssigned) {
                    Row row = assignedSheet.createRow(assignedRowNum++);
                    
                    // 删除用户ID字段，从第0列开始
                    row.createCell(0).setCellValue(dto.getUsername() != null ? dto.getUsername() : "");
                    row.createCell(1).setCellValue(dto.getName() != null ? dto.getName() : "");
                    row.createCell(2).setCellValue(dto.getInterviewDepartment() != null ? dto.getInterviewDepartment() : "");
                    
                    // 修改面试时间格式为 时间1-时间2，删除重复的日期显示
                    if (dto.getInterviewTime() != null) {
                        LocalDateTime startTime = dto.getInterviewTime();
                        LocalDateTime endTime = startTime.plusMinutes(10); // 面试时长10分钟
                        String timeRange = timeRangeFormat.format(startTime) + "-" + timeRangeFormat.format(endTime);
                        row.createCell(3).setCellValue(startTime.format(dtf) + " " + timeRange);
                    } else {
                        row.createCell(3).setCellValue("");
                    }
                    
                    row.createCell(4).setCellValue(dto.getPeriod() != null ? dto.getPeriod() : "");
                }
            }
            
            for (int i = 0; i < assignedHeaders.length; i++) {
                assignedSheet.autoSizeColumn(i);
            }

            // Sheet2: 未分配
            Sheet unassignedSheet = workbook.createSheet("未分配");
            Row unassignedHeader = unassignedSheet.createRow(0);
            // 删除用户ID字段
            String[] unassignedHeaders = {"用户名", "姓名", "期望时间", "期望部门"};
            for (int i = 0; i < unassignedHeaders.length; i++) {
                Cell cell = unassignedHeader.createCell(i);
                cell.setCellValue(unassignedHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int unassignedRowNum = 1;
            if (result != null && result.getUnassignedUsers() != null) {
                for (InterviewAssignmentResultDTO.UnassignedUserDTO dto : result.getUnassignedUsers()) {
                    Row row = unassignedSheet.createRow(unassignedRowNum++);
                    
                    // 删除用户ID字段
                    row.createCell(0).setCellValue(dto.getUsername() != null ? dto.getUsername() : "");
                    row.createCell(1).setCellValue(dto.getName() != null ? dto.getName() : "");
                    row.createCell(2).setCellValue(dto.getPreferredTimes() != null ? dto.getPreferredTimes() : "");
                    row.createCell(3).setCellValue(dto.getPreferredDepartments() != null ? dto.getPreferredDepartments() : "");
                }
            }
            
            for (int i = 0; i < unassignedHeaders.length; i++) {
                unassignedSheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
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