package club.boyuan.official.controller;

import club.boyuan.official.dto.PageResultDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.ResumeDTO;
import club.boyuan.official.dto.ResumeFieldValueDTO;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.entity.ResumeFieldValue;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IResumeFieldDefinitionService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import club.boyuan.official.utils.PdfExportUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简历控制器
 * 处理简历相关操作，包括字段定义管理、简历管理等
 */
@RestController
@RequestMapping("/api/resumes")
@AllArgsConstructor
public class ResumeController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);
    
    private final IResumeService resumeService;
    private final IResumeFieldDefinitionService fieldDefinitionService;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    
    /**
     * 获取指定招募周期的简历字段定义
     * @param cycleId 招募周期ID
     * @return 字段定义列表
     */
    @GetMapping("/fields/{cycleId}")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> getFieldDefinitions(
            @PathVariable Integer cycleId) {
        try {
            logger.info("获取ID为{}的招募周期的简历字段定义", cycleId);
            List<ResumeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
            logger.info("成功获取ID为{}的招募周期的简历字段定义，共{}条记录", cycleId, fieldDefinitions.size());
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取字段定义成功", fieldDefinitions));
        } catch (BusinessException e) {
            logger.warn("获取字段定义业务异常，招募周期ID: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取字段定义系统异常，招募周期ID: {}", cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取字段定义失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 创建字段定义
     * @param fieldDefinition 字段定义
     * @param request HTTP请求
     * @return 创建的字段定义
     */
    @PostMapping("/fields")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> createFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试创建字段定义，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            logger.info("管理员{}创建字段定义", username);
            ResumeFieldDefinition createdField = fieldDefinitionService.createFieldDefinition(fieldDefinition);
            return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义创建成功", createdField));
        } catch (BusinessException e) {
            logger.warn("创建字段定义业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("创建字段定义系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义创建失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 更新字段定义
     * @param fieldDefinition 字段定义
     * @param request HTTP请求
     * @return 更新的字段定义
     */
    @PutMapping("/fields")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> updateFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试更新字段定义，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            
            logger.info("管理员{}更新字段定义，字段ID: {}", username, fieldDefinition.getFieldId());
            ResumeFieldDefinition updatedField = fieldDefinitionService.updateFieldDefinition(fieldDefinition);
            return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义更新成功", updatedField));
        } catch (BusinessException e) {
            logger.warn("更新字段定义业务异常，字段ID: {}，错误码: {}，错误信息: {}", 
                    fieldDefinition.getFieldId(), e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("更新字段定义系统异常，字段ID: {}", fieldDefinition.getFieldId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义更新失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 批量更新字段定义
     * @param fieldDefinitions 字段定义列表
     * @param request HTTP请求
     * @return 更新的字段定义列表
     */
    @PutMapping("/fields/batch")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> batchUpdateFieldDefinitions(
            @RequestBody List<ResumeFieldDefinition> fieldDefinitions, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试批量更新字段定义，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            
            logger.info("管理员{}批量更新字段定义，字段数量: {}", username, fieldDefinitions.size());
            List<ResumeFieldDefinition> updatedFields = fieldDefinitionService.batchUpdateFieldDefinitions(fieldDefinitions);
            return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义批量更新成功", updatedFields));
        } catch (BusinessException e) {
            logger.warn("批量更新字段定义业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("批量更新字段定义系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义批量更新失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 删除字段定义
     * @param fieldId 字段ID
     * @param request HTTP请求
     * @return 删除结果
     */
    @DeleteMapping("/fields/{fieldId}")
    public ResponseEntity<ResponseMessage<String>> deleteFieldDefinition(
            @PathVariable Integer fieldId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试删除字段定义，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            
            logger.info("管理员{}删除字段定义，字段ID: {}", username, fieldId);
            fieldDefinitionService.deleteFieldDefinition(fieldId);
            return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义删除成功", "字段定义删除成功"));
        } catch (BusinessException e) {
            logger.warn("删除字段定义业务异常，字段ID: {}，错误码: {}，错误信息: {}", fieldId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("删除字段定义系统异常，字段ID: {}", fieldId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义删除失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据招募周期获取简历
     * @param cycleId 招募周期ID
     * @param request HTTP请求
     * @return 简历信息
     */
    @GetMapping("/cycle/{cycleId}")
    public ResponseEntity<ResponseMessage<?>> getResumeByCycleId(
            @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            Integer userId = getAuthenticatedUserIdFromRequest(request);
            User currentUser = userService.getUserById(userId);
            
            logger.info("用户{}({})获取招募周期ID为{}的简历", currentUser.getUsername(), userId, cycleId);
            ResumeDTO resumeDTO = resumeService.getResumeWithFieldValues(currentUser.getUserId(), cycleId);
            
            if (resumeDTO == null) {
                Resume resume = new Resume();
                resume.setUserId(currentUser.getUserId());
                resume.setCycleId(cycleId);
                resume.setStatus(1);
                resume.setCreatedAt(LocalDateTime.now());
                resume = resumeService.createResume(resume);
                
                resumeDTO = resumeService.getResumeWithFieldValues(currentUser.getUserId(), cycleId);
            }
            
            logger.info("用户{}({})成功获取招募周期ID为{}的简历", currentUser.getUsername(), userId, cycleId);
            return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
        } catch (BusinessException e) {
            logger.warn("获取简历业务异常，招募周期ID: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            Integer userId = null;
            String username = "unknown";
            try {
                userId = getAuthenticatedUserIdFromRequest(request);
                User currentUser = userService.getUserById(userId);
                username = currentUser.getUsername();
            } catch (Exception ex) {
                logger.warn("无法获取当前用户信息");
            }
            
            logger.error("获取简历系统异常，用户: {}({})，招募周期ID: {}", username, userId, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取简历失败: " + e.getMessage()));
        }
    }
    
    /**
     * 从请求中获取认证用户ID
     * @param request HTTP请求
     * @return 用户ID
     */
    private Integer getAuthenticatedUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        String username = jwtTokenUtil.extractUsername(token);
        User currentUser = userService.getUserByUsername(username);
        return currentUser.getUserId();
    }
    
    /**
     * 管理员查看指定用户的简历
     * @param userId 用户ID
     * @param cycleId 年份ID
     * @param request HTTP请求
     * @return 简历信息
     */
    @GetMapping("/admin/{userId}/{cycleId}")
    public ResponseEntity<ResponseMessage<?>> getResumeByUserIdAndCycleId(
            @PathVariable Integer userId, @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试查看用户{}的简历，但权限不足", username, userId);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            logger.info("管理员{}查看用户{}的{}年份简历", username, userId, cycleId);
            ResumeDTO resumeDTO = resumeService.getResumeWithFieldValues(userId, cycleId);
            
            if (resumeDTO == null) {
                logger.warn("简历不存在，用户ID: {}，年份: {}", userId, cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
        } catch (BusinessException e) {
            logger.warn("获取简历业务异常，用户ID: {}，年份: {}，错误码: {}，错误信息: {}", 
                    userId, cycleId, e.getCode(), e.getMessage());
            HttpStatus status = e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode() ? 
                    HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取简历系统异常，用户ID: {}，年份: {}", userId, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取简历失败: " + e.getMessage()));
        }
    }
    
    /**
     * 保存字段值
     * @param cycleId 招募周期ID
     * @param fieldValues 字段值列表
     * @param request HTTP请求
     * @return 保存结果
     */
    @PostMapping("/cycle/{cycleId}/field-values")
    public ResponseEntity<ResponseMessage<?>> saveFieldValues(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues,
            HttpServletRequest request) {
        try {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}保存招募周期ID为{}的简历字段值，字段数量: {}", username, cycleId, fieldValues.size());
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
        } catch (BusinessException e) {
            logger.warn("保存字段值业务异常，招募周期ID: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("保存字段值系统异常，用户: {}，招募周期ID: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "保存失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取字段值
     * @param cycleId 招募周期ID
     * @param request HTTP请求
     * @return 字段值列表
     */
    @GetMapping("/cycle/{cycleId}/field-values")
    public ResponseEntity<ResponseMessage<?>> getFieldValues(
            @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}获取招募周期ID为{}的简历字段值", username, cycleId);
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，招募周期ID: {}", currentUser.getUserId(), cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            List<ResumeFieldValueDTO> fieldValues = resumeService.getFieldValuesWithDefinitionsByResumeId(resume.getResumeId());
            
            return ResponseEntity.ok(ResponseMessage.success(fieldValues));
        } catch (BusinessException e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.warn("获取字段值业务异常，用户: {}，招募周期ID: {}，错误码: {}，错误信息: {}", username, cycleId, e.getCode(), e.getMessage());
            HttpStatus status;
            if (e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode()) {
                status = HttpStatus.NOT_FOUND;
            } else {
                status = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(status)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("获取字段值系统异常，用户: {}，招募周期ID: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取字段值失败: " + e.getMessage()));
        }
    }
    
    /**
     * 提交简历
     * @param cycleId 招募周期ID
     * @param request HTTP请求
     * @return 提交的简历
     */
    @PostMapping("/cycle/{cycleId}/submit")
    public ResponseEntity<ResponseMessage<?>> submitResume(
            @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}提交招募周期ID为{}的简历", username, cycleId);
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，招募周期ID: {}", currentUser.getUserId(), cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            if (resume.getStatus() != null && resume.getStatus() >= 2) {
                logger.warn("简历已提交或已在评审中，用户ID: {}，招募周期ID: {}，状态: {}", 
                        currentUser.getUserId(), cycleId, resume.getStatus());
                throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
            }
            
            Resume submittedResume = resumeService.submitResume(resume.getResumeId());
            
            return ResponseEntity.ok(ResponseMessage.success(submittedResume));
        } catch (BusinessException e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.warn("提交简历业务异常，用户: {}，招募周期ID: {}，错误码: {}，错误信息: {}", username, cycleId, e.getCode(), e.getMessage());
            HttpStatus status;
            if (e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode()) {
                status = HttpStatus.NOT_FOUND;
            } else {
                status = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(status)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("提交简历系统异常，用户: {}，招募周期ID: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历提交失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新简历状态
     * @param resumeId 简历ID
     * @param status 状态
     * @param request HTTP请求
     * @return 更新的简历
     */
    @PutMapping("/{resumeId}/status/{status}")
    public ResponseEntity<ResponseMessage<?>> updateResumeStatus(
            @PathVariable Integer resumeId, @PathVariable Integer status, HttpServletRequest request) {
        try {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试更新简历状态，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            logger.info("管理员{}更新简历{}状态为{}", username, resumeId, status);
            Resume resume = resumeService.getResumeById(resumeId);
            if (resume == null) {
                logger.warn("简历不存在，简历ID: {}", resumeId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            resume.setStatus(status);
            Resume updatedResume = resumeService.updateResume(resume);
            
            return ResponseEntity.ok(ResponseMessage.success(updatedResume));
        } catch (BusinessException e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.warn("更新简历状态业务异常，用户: {}，简历ID: {}，状态: {}，错误码: {}，错误信息: {}", 
                    username, resumeId, status, e.getCode(), e.getMessage());
            HttpStatus statusHttp;
            if (e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode()) {
                statusHttp = HttpStatus.NOT_FOUND;
            } else {
                statusHttp = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(statusHttp)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("更新简历状态系统异常，用户: {}，简历ID: {}，状态: {}", username, resumeId, status, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历状态更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新简历内容
     * @param cycleId 招募周期ID
     * @param fieldValues 字段值列表
     * @param request HTTP请求
     * @return 更新结果
     */
    @PutMapping("/cycle/{cycleId}")
    public ResponseEntity<ResponseMessage<?>> updateResume(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues,
            HttpServletRequest request) {
        try {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}更新招募周期ID为{}的简历，字段数量: {}", username, cycleId, fieldValues.size());
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，招募周期ID: {}", currentUser.getUserId(), cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            // 检查简历状态，已提交的简历不能更新
            if (resume.getStatus() != null && resume.getStatus() > 2) {
                logger.warn("尝试更新已提交的简历，用户ID: {}，招募周期ID: {}，状态: {}", 
                        currentUser.getUserId(), cycleId, resume.getStatus());
                throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
            }
            
            // 设置简历ID
            for (ResumeFieldValue fieldValue : fieldValues) {
                fieldValue.setResumeId(resume.getResumeId());
            }
            
            // 保存字段值
            resumeService.saveFieldValues(fieldValues);
            
            // 更新简历更新时间
            resume.setUpdatedAt(LocalDateTime.now());
            resumeService.updateResume(resume);
            
            return ResponseEntity.ok(ResponseMessage.success("简历更新成功"));
        } catch (BusinessException e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.warn("更新简历业务异常，用户: {}，招募周期ID: {}，错误码: {}，错误信息: {}", 
                    username, cycleId, e.getCode(), e.getMessage());
            HttpStatus status;
            if (e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode()) {
                status = HttpStatus.NOT_FOUND;
            } else {
                status = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(status)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("更新简历系统异常，用户: {}，招募周期ID: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除简历
     * @param resumeId 简历ID
     * @param request HTTP请求
     * @return 删除结果
     */
    @DeleteMapping("/{resumeId}")
    public ResponseEntity<ResponseMessage<String>> deleteResume(
            @PathVariable Integer resumeId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}尝试删除简历{}", username, resumeId);
            
            // 获取要删除的简历
            Resume resume = resumeService.getResumeById(resumeId);
            if (resume == null) {
                logger.warn("尝试删除不存在的简历，简历ID: {}", resumeId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            // 检查权限：管理员或简历所有者可以删除
            if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(resume.getUserId())) {
                logger.warn("用户{}尝试删除不属于自己的简历{}", username, resumeId);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            // 执行删除操作
            resumeService.deleteResume(resumeId);
            
            logger.info("用户{}成功删除简历{}", username, resumeId);
            return ResponseEntity.ok(new ResponseMessage<>(200, "简历删除成功", "简历删除成功"));
        } catch (BusinessException e) {
            logger.warn("删除简历业务异常，简历ID: {}，错误码: {}，错误信息: {}", resumeId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("删除简历系统异常，用户: {}，简历ID: {}", username, resumeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历删除失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 导出简历为PDF格式
     * @param resumeId 简历ID
     * @param request HTTP请求
     * @param response HTTP响应
     */
    @GetMapping("/export/pdf/{resumeId}")
    public void exportResumeToPdf(
            @PathVariable Integer resumeId,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            // 检查认证头
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("导出简历请求缺少有效的认证头");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未提供令牌");
                return;
            }
            
            try {
                String token = authHeader.substring(7);
                String username = jwtTokenUtil.extractUsername(token);
                User currentUser = userService.getUserByUsername(username);
                
                // 获取简历信息
                ResumeDTO resumeDTO = resumeService.getResumeWithFieldValuesById(resumeId);
                
                // 检查简历是否存在
                if (resumeDTO == null) {
                    logger.warn("尝试导出不存在的简历，简历ID: {}，用户: {}", resumeId, username);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "简历不存在");
                    return;
                }
                
                // 权限检查：管理员或简历所有者可以导出
                if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(resumeDTO.getUserId())) {
                    logger.warn("用户{}尝试导出不属于自己的简历{}", username, resumeId);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "权限不足");
                    return;
                }
                
                // 生成PDF
                byte[] pdfBytes = PdfExportUtil.exportResumeToPdf(resumeDTO);
                
                // 设置响应头
                response.setContentType("application/pdf");
                response.setHeader("Content-Disposition", "attachment; filename=resume_" + resumeId + ".pdf");
                response.setContentLength(pdfBytes.length);
                
                // 写入响应
                response.getOutputStream().write(pdfBytes);
                response.getOutputStream().flush();
                
                logger.info("用户{}成功导出简历{}为PDF", username, resumeId);
            } catch (Exception e) {
                logger.error("令牌验证失败", e);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "令牌验证失败");
                return;
            }
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
     * 条件查询简历列表
     * 支持按姓名、专业、招募周期、状态等多条件组合查询
     */
    @GetMapping("/search")
    public ResponseEntity<ResponseMessage<?>> queryResumes(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) Integer cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            String username = "unknown";
            if (token != null && token.startsWith("Bearer ")) {
                username = jwtTokenUtil.extractUsername(token.substring(7));
            }
            User currentUser = userService.getUserByUsername(username);
            // 仅管理员可用
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试条件查询简历，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            // 如果page和size参数都为默认值，则使用原来的查询方法（保持向后兼容）
            if (page == 0 && size == 10) {
                List<ResumeDTO> result = resumeService.queryResumes(name, major, cycleId, status);
                logger.info("管理员{}执行条件查询简历，结果数量: {}", username, result.size());
                return ResponseEntity.ok(ResponseMessage.success(result));
            } else {
                // 使用分页查询
                PageResultDTO<ResumeDTO> result = resumeService.queryResumesWithPagination(name, major, cycleId, status, page, size);
                logger.info("管理员{}执行分页条件查询简历，结果数量: {}，总记录数: {}", username, result.getContent().size(), result.getTotalElements());
                return ResponseEntity.ok(ResponseMessage.success(result));
            }
        } catch (BusinessException e) {
            logger.warn("条件查询简历业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("条件查询简历系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), "条件查询简历失败: " + e.getMessage()));
        }
    }
}