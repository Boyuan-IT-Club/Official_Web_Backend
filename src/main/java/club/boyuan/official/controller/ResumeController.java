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

@RestController
@RequestMapping("/api/resumes")
@AllArgsConstructor
public class ResumeController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);
    
    private final IResumeService resumeService;
    private final IResumeFieldDefinitionService fieldDefinitionService;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    
    @GetMapping("/fields/{cycleId}")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> getFieldDefinitions(
            @PathVariable Integer cycleId) {
        try {
            logger.info("获取{}年份的简历字段定义", cycleId);
            List<ResumeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取字段定义成功", fieldDefinitions));
        } catch (BusinessException e) {
            logger.warn("获取字段定义业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取字段定义系统异常，年份: {}", cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取字段定义失败: " + e.getMessage(), null));
        }
    }
    
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
    
    @PutMapping("/fields")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> updateFieldDefinition(
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
    
    @DeleteMapping("/fields/{fieldId}")
    public ResponseEntity<ResponseMessage<String>> deleteFieldDefinition(
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
    
    @GetMapping("/cycle/{cycleId}")
    public ResponseEntity<ResponseMessage<ResumeDTO>> getResumeByCycleId(
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
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取简历成功", resumeDTO));
        } catch (BusinessException e) {
            logger.warn("获取简历业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取简历系统异常，用户ID: {}，年份: {}", 
                    request.getHeader("Authorization") != null ? 
                            jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7)) : "unknown", 
                    cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取简历失败: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/admin/{userId}/{cycleId}")
    public ResponseEntity<ResponseMessage<ResumeDTO>> getResumeByUserIdAndCycleId(
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
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取简历成功", resumeDTO));
        } catch (BusinessException e) {
            logger.warn("获取简历业务异常，用户ID: {}，年份: {}，错误码: {}，错误信息: {}", 
                    userId, cycleId, e.getCode(), e.getMessage());
            HttpStatus status = e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode() ? 
                    HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取简历系统异常，用户ID: {}，年份: {}", userId, cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取简历失败: " + e.getMessage(), null));
        }
    }
    
    @PostMapping("/cycle/{cycleId}/field-values")
    public ResponseEntity<ResponseMessage<String>> saveFieldValues(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues,
            HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
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
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "保存成功", "字段值保存成功"));
        } catch (BusinessException e) {
            logger.warn("保存字段值业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("保存字段值系统异常，用户ID: {}，年份: {}", 
                    request.getHeader("Authorization") != null ? 
                            jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7)) : "unknown", 
                    cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "保存失败: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/cycle/{cycleId}/field-values")
    public ResponseEntity<ResponseMessage<List<ResumeFieldValueDTO>>> getFieldValues(
            @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            
            logger.info("用户{}获取{}年份简历字段值", username, cycleId);
            Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
            if (resume == null) {
                logger.warn("简历不存在，用户ID: {}，年份: {}", currentUser.getUserId(), cycleId);
                throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
            }
            
            List<ResumeFieldValueDTO> fieldValues = resumeService.getFieldValuesWithDefinitionsByResumeId(resume.getResumeId());
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取字段值成功", fieldValues));
        } catch (BusinessException e) {
            logger.warn("获取字段值业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            HttpStatus status = e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode() ? 
                    HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取字段值系统异常，用户ID: {}，年份: {}", 
                    request.getHeader("Authorization") != null ? 
                            jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7)) : "unknown", 
                    cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取字段值失败: " + e.getMessage(), null));
        }
    }
    
    @PostMapping("/cycle/{cycleId}/submit")
    public ResponseEntity<ResponseMessage<Resume>> submitResume(
            @PathVariable Integer cycleId, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
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
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "简历提交成功", submittedResume));
        } catch (BusinessException e) {
            logger.warn("提交简历业务异常，年份: {}，错误码: {}，错误信息: {}", cycleId, e.getCode(), e.getMessage());
            HttpStatus status = e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode() || 
                               e.getCode() == BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED.getCode() ? 
                    HttpStatus.BAD_REQUEST : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("提交简历系统异常，用户ID: {}，年份: {}", 
                    request.getHeader("Authorization") != null ? 
                            jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7)) : "unknown", 
                    cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历提交失败: " + e.getMessage(), null));
        }
    }
    
    @PutMapping("/{resumeId}/status/{status}")
    public ResponseEntity<ResponseMessage<Resume>> updateResumeStatus(
            @PathVariable Integer resumeId, @PathVariable Integer status, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            String username = jwtTokenUtil.extractUsername(token);
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
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "简历状态更新成功", updatedResume));
        } catch (BusinessException e) {
            logger.warn("更新简历状态业务异常，简历ID: {}，状态: {}，错误码: {}，错误信息: {}", 
                    resumeId, status, e.getCode(), e.getMessage());
            HttpStatus statusHttp = e.getCode() == BusinessExceptionEnum.RESUME_NOT_FOUND.getCode() ? 
                    HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(statusHttp)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("更新简历状态系统异常，简历ID: {}，状态: {}", resumeId, status, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "简历状态更新失败: " + e.getMessage(), null));
        }
    }
}