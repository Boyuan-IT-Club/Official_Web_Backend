package club.boyuan.official.controller;

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
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
     * 获取字段定义列表
     * @param cycleId 年份ID
     * @return 字段定义列表
     */
    @GetMapping("/fields/{cycleId}")
    public ResponseEntity<ResponseMessage<?>> getFieldDefinitions(
            @PathVariable Integer cycleId) {
        try {
            logger.info("获取{}年份的简历字段定义", cycleId);
            List<ResumeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
            return ResponseEntity.ok(ResponseMessage.success(fieldDefinitions));
        } catch (BusinessException e) {
            logger.warn("获取字段定义业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取字段定义系统异常，年份: {}", cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取字段定义失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建字段定义
     * @param fieldDefinition 字段定义
     * @param request HTTP请求
     * @return 创建的字段定义
     */
    @PostMapping("/fields")
    public ResponseEntity<ResponseMessage<?>> createFieldDefinition(
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
            return ResponseEntity.ok(ResponseMessage.success(createdField));
        } catch (BusinessException e) {
            logger.warn("创建字段定义业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("创建字段定义系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新字段定义
     * @param fieldDefinition 字段定义
     * @param request HTTP请求
     * @return 更新的字段定义
     */
    @PutMapping("/fields")
    public ResponseEntity<ResponseMessage<?>> updateFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试更新字段定义，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            logger.info("管理员{}更新字段定义，字段ID: {}", username, fieldDefinition.getFieldId());
            ResumeFieldDefinition updatedField = fieldDefinitionService.updateFieldDefinition(fieldDefinition);
            return ResponseEntity.ok(ResponseMessage.success(updatedField));
        } catch (BusinessException e) {
            logger.warn("更新字段定义业务异常，字段ID: {}，错误码: {}，错误信息: {}", 
                    fieldDefinition.getFieldId(), e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新字段定义系统异常，字段ID: {}", fieldDefinition.getFieldId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除字段定义
     * @param fieldId 字段ID
     * @param request HTTP请求
     * @return 删除结果
     */
    @DeleteMapping("/fields/{fieldId}")
    public ResponseEntity<ResponseMessage<?>> deleteFieldDefinition(
            @PathVariable Integer fieldId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户{}尝试删除字段定义，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
            }
            
            logger.info("管理员{}删除字段定义，字段ID: {}", username, fieldId);
            fieldDefinitionService.deleteFieldDefinition(fieldId);
            return ResponseEntity.ok(ResponseMessage.success("字段定义删除成功"));
        } catch (BusinessException e) {
            logger.warn("删除字段定义业务异常，字段ID: {}，错误码: {}，错误信息: {}", fieldId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("删除字段定义系统异常，字段ID: {}", fieldId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "字段定义删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据年份获取简历
     * @param cycleId 年份ID
     * @param request HTTP请求
     * @return 简历信息
     */
    @GetMapping("/cycle/{cycleId}")
    public ResponseEntity<ResponseMessage<?>> getResumeByCycleId(
            @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}获取{}年份简历", username, cycleId);
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
            
            return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
        } catch (BusinessException e) {
            logger.warn("获取简历业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取简历系统异常，用户ID: {}，年份: {}", 
                    request.getHeader("Authorization") != null ? 
                            jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7)) : "unknown", 
                    cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取简历失败: " + e.getMessage()));
        }
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
     * @param cycleId 年份ID
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
            
            logger.info("用户{}保存{}年份简历字段值，字段数量: {}", username, cycleId, fieldValues.size());
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
            logger.warn("保存字段值业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
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
            
            logger.error("保存字段值系统异常，用户: {}，年份: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "保存失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取字段值
     * @param cycleId 年份ID
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
            
            logger.info("用户{}获取{}年份简历字段值", username, cycleId);
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，年份: {}", currentUser.getUserId(), cycleId);
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
            
            logger.warn("获取字段值业务异常，用户: {}，年份: {}，错误码: {}，错误信息: {}", username, cycleId, e.getCode(), e.getMessage());
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
            
            logger.error("获取字段值系统异常，用户: {}，年份: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取字段值失败: " + e.getMessage()));
        }
    }
    
    /**
     * 提交简历
     * @param cycleId 年份ID
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
            
            logger.info("用户{}提交{}年份简历", username, cycleId);
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，年份: {}", currentUser.getUserId(), cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            if (resume.getStatus() != null && resume.getStatus() >= 2) {
                logger.warn("简历已提交或已在评审中，用户ID: {}，年份: {}，状态: {}", 
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
            
            logger.warn("提交简历业务异常，用户: {}，年份: {}，错误码: {}，错误信息: {}", username, cycleId, e.getCode(), e.getMessage());
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
            
            logger.error("提交简历系统异常，用户: {}，年份: {}", username, cycleId, e);
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
     * @param cycleId 年份ID
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
            
            logger.info("用户{}更新{}年份简历，字段数量: {}", username, cycleId, fieldValues.size());
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，年份: {}", currentUser.getUserId(), cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            // 检查简历状态，已提交的简历不能更新
            if (resume.getStatus() != null && resume.getStatus() >= 2) {
                logger.warn("尝试更新已提交的简历，用户ID: {}，年份: {}，状态: {}", 
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
            
            logger.warn("更新简历业务异常，用户: {}，年份: {}，错误码: {}，错误信息: {}", 
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
            
            logger.error("更新简历系统异常，用户: {}，年份: {}", username, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历更新失败: " + e.getMessage()));
        }
    }
}