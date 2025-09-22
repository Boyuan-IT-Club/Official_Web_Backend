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
import java.util.ArrayList;
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
            System.out.println("开始导出Excel，用户数量: " + (users != null ? users.size() : 0));
            
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("用户列表");
            
            // 创建标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setCharSet(Font.ANSI_CHARSET); // 设置字符集支持
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // 创建数据行样式
            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setCharSet(Font.ANSI_CHARSET); // 设置字符集支持
            dataStyle.setFont(dataFont);
            
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
                    
                    // 使用dataStyle并确保中文显示正确
                    Cell cell0 = row.createCell(0);
                    cell0.setCellValue(user.getUserId() != null ? user.getUserId() : 0);
                    cell0.setCellStyle(dataStyle);
                    
                    Cell cell1 = row.createCell(1);
                    cell1.setCellValue(user.getUsername() != null ? user.getUsername() : "");
                    cell1.setCellStyle(dataStyle);
                    
                    Cell cell2 = row.createCell(2);
                    cell2.setCellValue(user.getName() != null ? user.getName() : "");
                    cell2.setCellStyle(dataStyle);
                    
                    Cell cell3 = row.createCell(3);
                    cell3.setCellValue(user.getEmail() != null ? user.getEmail() : "");
                    cell3.setCellStyle(dataStyle);
                    
                    Cell cell4 = row.createCell(4);
                    cell4.setCellValue(user.getPhone() != null ? user.getPhone() : "");
                    cell4.setCellStyle(dataStyle);
                    
                    Cell cell5 = row.createCell(5);
                    cell5.setCellValue(user.getMajor() != null ? user.getMajor() : "");
                    cell5.setCellStyle(dataStyle);
                    
                    Cell cell6 = row.createCell(6);
                    cell6.setCellValue(user.getGithub() != null ? user.getGithub() : "");
                    cell6.setCellStyle(dataStyle);
                    
                    Cell cell7 = row.createCell(7);
                    cell7.setCellValue(user.getDept() != null ? user.getDept() : "");
                    cell7.setCellStyle(dataStyle);
                    
                    Cell cell8 = row.createCell(8);
                    cell8.setCellValue(user.getRole() != null ? user.getRole() : "");
                    cell8.setCellStyle(dataStyle);
                    
                    Cell cell9 = row.createCell(9);
                    cell9.setCellValue(user.getStatus() != null ? (user.getStatus() ? "激活" : "冻结") : "未知");
                    cell9.setCellStyle(dataStyle);
                    
                    Cell cell10 = row.createCell(10);
                    cell10.setCellValue(user.getIsMember() != null ? (user.getIsMember() ? "是" : "否") : "未知");
                    cell10.setCellStyle(dataStyle);
                    
                    Cell cell11 = row.createCell(11);
                    cell11.setCellValue(user.getCreateTime() != null ? 
                        user.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
                    cell11.setCellStyle(dataStyle);
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
            System.err.println("Excel导出失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException(BusinessExceptionEnum.EXPORT_EXCEL_FAILED, 
                "Excel导出失败: " + e.getMessage());
        }
    }

    /**
     * 将面试安排结果导出为Excel，按教室分布显示
     * @param result 面试安排结果
     * @return Excel字节数组
     */
    public static byte[] exportInterviewAssignmentsToExcel(InterviewAssignmentResultDTO result) throws BusinessException {
        try {
            System.out.println("开始导出面试安排Excel...");
            
            Workbook workbook = new XSSFWorkbook();

            // 通用样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setCharSet(Font.ANSI_CHARSET); // 设置字符集支持
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // 数据行样式
            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setCharSet(Font.ANSI_CHARSET); // 设置字符集支持
            dataStyle.setFont(dataFont);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeRangeFormat = DateTimeFormatter.ofPattern("HH:mm");

            // 按教室分组已分配的面试
            Map<String, List<InterviewAssignmentResultDTO.AssignedInterviewDTO>> classroomGroups = new TreeMap<>();
            if (result != null && result.getAssignedInterviews() != null) {
                for (InterviewAssignmentResultDTO.AssignedInterviewDTO dto : result.getAssignedInterviews()) {
                    String classroom = dto.getClassroom() != null ? dto.getClassroom() : "未知教室";
                    classroomGroups.computeIfAbsent(classroom, k -> new ArrayList<>()).add(dto);
                }
            }

            // 为每个教室创建工作表
            for (Map.Entry<String, List<InterviewAssignmentResultDTO.AssignedInterviewDTO>> entry : classroomGroups.entrySet()) {
                String classroom = entry.getKey();
                List<InterviewAssignmentResultDTO.AssignedInterviewDTO> assignments = entry.getValue();
                
                Sheet classroomSheet = workbook.createSheet(classroom);
                Row classroomHeader = classroomSheet.createRow(0);
                String[] classroomHeaders = {"用户名", "姓名", "邮箱", "所属部门", "面试时间", "时间段"};
                
                for (int i = 0; i < classroomHeaders.length; i++) {
                    Cell cell = classroomHeader.createCell(i);
                    cell.setCellValue(classroomHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }
                
                // 按部门和时间排序
                List<InterviewAssignmentResultDTO.AssignedInterviewDTO> sortedAssignments = assignments.stream()
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
                
                int rowNum = 1;
                for (InterviewAssignmentResultDTO.AssignedInterviewDTO dto : sortedAssignments) {
                    Row row = classroomSheet.createRow(rowNum++);
                    
                    Cell cell0 = row.createCell(0);
                    cell0.setCellValue(dto.getUsername() != null ? dto.getUsername() : "");
                    cell0.setCellStyle(dataStyle);
                    
                    Cell cell1 = row.createCell(1);
                    cell1.setCellValue(dto.getName() != null ? dto.getName() : "");
                    cell1.setCellStyle(dataStyle);
                    
                    Cell cell2 = row.createCell(2);
                    cell2.setCellValue(dto.getEmail() != null ? dto.getEmail() : "");
                    cell2.setCellStyle(dataStyle);
                    
                    Cell cell3 = row.createCell(3);
                    cell3.setCellValue(dto.getInterviewDepartment() != null ? dto.getInterviewDepartment() : "");
                    cell3.setCellStyle(dataStyle);
                    
                    Cell cell4 = row.createCell(4);
                    // 修改面试时间格式为 时间1-时间2
                    if (dto.getInterviewTime() != null) {
                        LocalDateTime startTime = dto.getInterviewTime();
                        LocalDateTime endTime = startTime.plusMinutes(10); // 面试时长10分钟
                        String timeRange = timeRangeFormat.format(startTime) + "-" + timeRangeFormat.format(endTime);
                        cell4.setCellValue(startTime.format(dtf) + " " + timeRange);
                    } else {
                        cell4.setCellValue("");
                    }
                    cell4.setCellStyle(dataStyle);
                    
                    Cell cell5 = row.createCell(5);
                    cell5.setCellValue(dto.getPeriod() != null ? dto.getPeriod() : "");
                    cell5.setCellStyle(dataStyle);
                }
                
                for (int i = 0; i < classroomHeaders.length; i++) {
                    classroomSheet.autoSizeColumn(i);
                }
            }

            // Sheet: 未分配
            Sheet unassignedSheet = workbook.createSheet("未分配");
            Row unassignedHeader = unassignedSheet.createRow(0);
            String[] unassignedHeaders = {"用户名", "姓名", "邮箱", "期望时间", "期望部门"};
            for (int i = 0; i < unassignedHeaders.length; i++) {
                Cell cell = unassignedHeader.createCell(i);
                cell.setCellValue(unassignedHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int unassignedRowNum = 1;
            if (result != null && result.getUnassignedUsers() != null) {
                for (InterviewAssignmentResultDTO.UnassignedUserDTO dto : result.getUnassignedUsers()) {
                    Row row = unassignedSheet.createRow(unassignedRowNum++);
                    
                    row.createCell(0).setCellValue(dto.getUsername() != null ? dto.getUsername() : "");
                    row.createCell(1).setCellValue(dto.getName() != null ? dto.getName() : "");
                    row.createCell(2).setCellValue(dto.getEmail() != null ? dto.getEmail() : "");
                    row.createCell(3).setCellValue(dto.getPreferredTimes() != null ? dto.getPreferredTimes() : "");
                    row.createCell(4).setCellValue(dto.getPreferredDepartments() != null ? dto.getPreferredDepartments() : "");
                }
            }
            
            for (int i = 0; i < unassignedHeaders.length; i++) {
                unassignedSheet.autoSizeColumn(i);
            }
            
            // Sheet: 未填写期望
            Sheet noPreferenceSheet = workbook.createSheet("未填写期望");
            Row noPreferenceHeader = noPreferenceSheet.createRow(0);
            String[] noPreferenceHeaders = {"用户名", "姓名", "邮箱"};
            for (int i = 0; i < noPreferenceHeaders.length; i++) {
                Cell cell = noPreferenceHeader.createCell(i);
                cell.setCellValue(noPreferenceHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int noPreferenceRowNum = 1;
            if (result != null && result.getNoPreferenceUsers() != null) {
                for (InterviewAssignmentResultDTO.NoPreferenceUserDTO dto : result.getNoPreferenceUsers()) {
                    Row row = noPreferenceSheet.createRow(noPreferenceRowNum++);
                    
                    row.createCell(0).setCellValue(dto.getUsername() != null ? dto.getUsername() : "");
                    row.createCell(1).setCellValue(dto.getName() != null ? dto.getName() : "");
                    row.createCell(2).setCellValue(dto.getEmail() != null ? dto.getEmail() : "");
                }
            }
            
            for (int i = 0; i < noPreferenceHeaders.length; i++) {
                noPreferenceSheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                workbook.write(baos);
                return baos.toByteArray();
            } finally {
                workbook.close();
            }
        } catch (Exception e) {
            System.err.println("面试安排Excel导出失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException(BusinessExceptionEnum.EXPORT_EXCEL_FAILED, 
                "面试安排Excel导出失败: " + e.getMessage());
        }
    }
}