package club.boyuan.official.domain.resume.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.ResumeFieldValueDTO;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.PdfExportUtil;
import club.boyuan.official.common.utils.PermissionUtils;
import club.boyuan.official.common.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历控制器
 * 处理简历相关操作，包括字段定义管理、简历管理等。
 * <p>
 * 认证由 JwtAuthenticationFilter 统一完成、鉴权由 {@link PreAuthorize} 保证、
 * 异常由 GlobalExceptionHandler 统一处理，Controller 只表达正常业务流程。
 */
@RestController
@RequestMapping("/api/resumes")
@AllArgsConstructor
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    private final IResumeService resumeService;
    private final IResumeFieldDefinitionService fieldDefinitionService;
    private final IUserService userService;

    /**
     * 获取指定招募周期的简历字段定义
     */
    @GetMapping("/fields/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> getFieldDefinitions(
            @PathVariable Integer cycleId) {
        logger.info("获取ID为{}的招募周期的简历字段定义", cycleId);
        List<ResumeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        logger.info("成功获取ID为{}的招募周期的简历字段定义，共{}条记录", cycleId, fieldDefinitions.size());
        return ResponseEntity.ok(new ResponseMessage<>(200, "获取字段定义成功", fieldDefinitions));
    }

    /**
     * 创建字段定义
     */
    @PostMapping("/fields")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> createFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition) {
        logger.info("创建字段定义");
        ResumeFieldDefinition createdField = fieldDefinitionService.createFieldDefinition(fieldDefinition);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义创建成功", createdField));
    }

    /**
     * 更新字段定义
     */
    @PutMapping("/fields")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> updateFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition) {
        logger.info("更新字段定义，字段ID: {}", fieldDefinition.getFieldId());
        ResumeFieldDefinition updatedField = fieldDefinitionService.updateFieldDefinition(fieldDefinition);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义更新成功", updatedField));
    }

    /**
     * 批量更新字段定义
     */
    @PutMapping("/fields/batch")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> batchUpdateFieldDefinitions(
            @RequestBody List<ResumeFieldDefinition> fieldDefinitions) {
        logger.info("批量更新字段定义，字段数量: {}", fieldDefinitions.size());
        List<ResumeFieldDefinition> updatedFields = fieldDefinitionService.batchUpdateFieldDefinitions(fieldDefinitions);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义批量更新成功", updatedFields));
    }

    /**
     * 删除字段定义
     */
    @DeleteMapping("/fields/{fieldId}")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<String>> deleteFieldDefinition(@PathVariable Integer fieldId) {
        logger.info("管理员{}删除字段定义，字段ID: {}", SecurityUtil.getCurrentUsername(), fieldId);
        fieldDefinitionService.deleteFieldDefinition(fieldId);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义删除成功", "字段定义删除成功"));
    }

    /**
     * 根据招募周期获取当前用户的简历（不存在则自动创建草稿）
     */
    @GetMapping("/cycle/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> getResumeByCycleId(@PathVariable Integer cycleId) {
        User currentUser = currentUser();
        logger.info("用户{}({})获取招募周期ID为{}的简历", currentUser.getUsername(), currentUser.getUserId(), cycleId);

        ResumeDTO resumeDTO = resumeService.getResumeWithFieldValues(currentUser.getUserId(), cycleId);
        if (resumeDTO == null) {
            Resume resume = new Resume();
            resume.setUserId(currentUser.getUserId());
            resume.setCycleId(cycleId);
            resume.setStatus(1);
            resume.setCreatedAt(LocalDateTime.now());
            resumeService.createResume(resume);
            resumeDTO = resumeService.getResumeWithFieldValues(currentUser.getUserId(), cycleId);
        }

        logger.info("用户{}({})成功获取招募周期ID为{}的简历", currentUser.getUsername(), currentUser.getUserId(), cycleId);
        return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
    }

    /**
     * 管理员查看指定用户的简历
     */
    @GetMapping("/admin/{userId}/{cycleId}")
    @PreAuthorize("hasAuthority('resume:view')")
    public ResponseEntity<ResponseMessage<?>> getResumeByUserIdAndCycleId(
            @PathVariable Integer userId, @PathVariable Integer cycleId) {
        logger.info("管理员{}查看用户{}的{}年份简历", SecurityUtil.getCurrentUsername(), userId, cycleId);
        ResumeDTO resumeDTO = resumeService.getResumeWithFieldValues(userId, cycleId);
        if (resumeDTO == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
    }

    /**
     * 保存字段值（不存在简历则自动创建草稿）
     */
    @PostMapping("/cycle/{cycleId}/field-values")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> saveFieldValues(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues) {
        User currentUser = currentUser();
        logger.info("用户{}保存招募周期ID为{}的简历字段值，字段数量: {}",
                currentUser.getUsername(), cycleId, fieldValues.size());

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            resume = new Resume();
            resume.setUserId(currentUser.getUserId());
            resume.setCycleId(cycleId);
            resume.setStatus(1);
            resume.setCreatedAt(LocalDateTime.now());
            resume = resumeService.createResume(resume);
        }

        for (ResumeFieldValue fieldValue : fieldValues) {
            fieldValue.setResumeId(resume.getResumeId());
        }
        resumeService.saveFieldValues(fieldValues);

        return ResponseEntity.ok(ResponseMessage.success("字段值保存成功"));
    }

    /**
     * 获取当前用户字段值
     */
    @GetMapping("/cycle/{cycleId}/field-values")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> getFieldValues(@PathVariable Integer cycleId) {
        User currentUser = currentUser();
        logger.info("用户{}获取招募周期ID为{}的简历字段值", currentUser.getUsername(), cycleId);

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }

        List<ResumeFieldValueDTO> fieldValues =
                resumeService.getFieldValuesWithDefinitionsByResumeId(resume.getResumeId());
        return ResponseEntity.ok(ResponseMessage.success(fieldValues));
    }

    /**
     * 提交简历
     */
    @PostMapping("/cycle/{cycleId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> submitResume(@PathVariable Integer cycleId) {
        User currentUser = currentUser();
        logger.info("用户{}提交招募周期ID为{}的简历", currentUser.getUsername(), cycleId);

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (resume.getStatus() != null && resume.getStatus() >= 2) {
            logger.warn("简历已提交或已在评审中，用户ID: {}，招募周期ID: {}，状态: {}",
                    currentUser.getUserId(), cycleId, resume.getStatus());
            throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
        }

        Resume submittedResume = resumeService.submitResume(resume.getResumeId());
        return ResponseEntity.ok(ResponseMessage.success(submittedResume));
    }

    /**
     * 更新简历状态（管理员）
     */
    @PutMapping("/{resumeId}/status/{status}")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> updateResumeStatus(
            @PathVariable Integer resumeId, @PathVariable Integer status) {
        logger.info("管理员{}更新简历{}状态为{}", SecurityUtil.getCurrentUsername(), resumeId, status);

        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        resume.setStatus(status);
        Resume updatedResume = resumeService.updateResume(resume);
        return ResponseEntity.ok(ResponseMessage.success(updatedResume));
    }

    /**
     * 更新简历内容（未提交状态才可更新）
     */
    @PutMapping("/cycle/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> updateResume(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues) {
        User currentUser = currentUser();
        logger.info("用户{}更新招募周期ID为{}的简历，字段数量: {}",
                currentUser.getUsername(), cycleId, fieldValues.size());

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (resume.getStatus() != null && resume.getStatus() > 2) {
            logger.warn("尝试更新已提交的简历，用户ID: {}，招募周期ID: {}，状态: {}",
                    currentUser.getUserId(), cycleId, resume.getStatus());
            throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
        }

        for (ResumeFieldValue fieldValue : fieldValues) {
            fieldValue.setResumeId(resume.getResumeId());
        }
        resumeService.saveFieldValues(fieldValues);

        resume.setUpdatedAt(LocalDateTime.now());
        resumeService.updateResume(resume);

        return ResponseEntity.ok(ResponseMessage.success("简历更新成功"));
    }

    /**
     * 删除简历（管理员或本人）
     */
    @DeleteMapping("/{resumeId}")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<String>> deleteResume(@PathVariable Integer resumeId) {
        User currentUser = currentUser();
        logger.info("用户{}尝试删除简历{}", currentUser.getUsername(), resumeId);

        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (!PermissionUtils.canAccessUserResource(currentUser, resume.getUserId())) {
            logger.warn("用户{}尝试删除不属于自己的简历{}", currentUser.getUsername(), resumeId);
            throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
        }

        resumeService.deleteResume(resumeId);
        logger.info("用户{}成功删除简历{}", currentUser.getUsername(), resumeId);
        return ResponseEntity.ok(new ResponseMessage<>(200, "简历删除成功", "简历删除成功"));
    }

    /**
     * 导出简历为PDF格式（管理员或本人）。
     * 因直接向响应流写二进制，保留局部异常处理（无法交给 GlobalExceptionHandler 处理半写响应）。
     */
    @GetMapping("/export/pdf/{resumeId}")
    @PreAuthorize("isAuthenticated()")  // 归属校验在方法内：本人或具备管理权限者可导出
    public void exportResumeToPdf(@PathVariable Integer resumeId, HttpServletResponse response) {
        try {
            User currentUser = currentUser();

            ResumeDTO resumeDTO = resumeService.getResumeWithFieldValuesById(resumeId);
            if (resumeDTO == null) {
                logger.warn("尝试导出不存在的简历，简历ID: {}，用户: {}", resumeId, currentUser.getUsername());
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "简历不存在");
                return;
            }
            if (!PermissionUtils.canAccessUserResource(currentUser, resumeDTO.getUserId())) {
                logger.warn("用户{}尝试导出不属于自己的简历{}", currentUser.getUsername(), resumeId);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "权限不足");
                return;
            }

            byte[] pdfBytes = PdfExportUtil.exportResumeToPdf(resumeDTO);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=resume_" + resumeId + ".pdf");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();

            logger.info("用户{}成功导出简历{}为PDF", currentUser.getUsername(), resumeId);
        } catch (Exception e) {
            logger.error("导出简历为PDF失败，简历ID: {}", resumeId, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 条件查询简历列表（管理员）。
     * 支持按姓名、专业、期望部门、招募周期、状态等多条件组合查询。
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('resume:view')")
    public ResponseEntity<ResponseMessage<?>> queryResumes(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String expectedDepartment,
            @RequestParam(required = false) Integer cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        String username = SecurityUtil.getCurrentUsername();

        // 若 page/size 均为默认且未指定排序，使用非分页查询（保持向后兼容）
        if (page == 0 && size == 10 && sortBy == null) {
            List<ResumeDTO> result = resumeService.queryResumes(name, major, expectedDepartment, cycleId, status);
            logger.info("管理员{}执行条件查询简历，结果数量: {}", username, result.size());
            return ResponseEntity.ok(ResponseMessage.success(result));
        }
        PageResultDTO<ResumeDTO> result = resumeService.queryResumesWithPagination(
                name, major, expectedDepartment, cycleId, status, page, size, sortBy, sortOrder);
        logger.info("管理员{}执行分页条件查询简历，结果数量: {}，总记录数: {}",
                username, result.getContent().size(), result.getTotalElements());
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 获取当前登录用户实体。令牌已由过滤器校验，这里只从 SecurityContext 读取用户名并查用户。
     */
    private User currentUser() {
        return userService.getUserByUsername(SecurityUtil.getCurrentUsername());
    }
}
