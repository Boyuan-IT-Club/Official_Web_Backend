package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.RecruitmentCycle;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IRecruitmentCycleService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招募周期Controller
 */
@RestController
@RequestMapping("/api/cycles")
@AllArgsConstructor
public class RecruitmentCycleController {
    
    private static final Logger logger = LoggerFactory.getLogger(RecruitmentCycleController.class);
    
    private final IRecruitmentCycleService recruitmentCycleService;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    private final HttpServletRequest request;
    
    /**
     * 创建招募周期（仅管理员）
     */
    @PostMapping
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> createRecruitmentCycle(@RequestBody RecruitmentCycle recruitmentCycle) {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser();
            checkAdminPermission(currentUser);
            
            logger.info("管理员{}创建招募周期", currentUser.getUsername());
            RecruitmentCycle createdCycle = recruitmentCycleService.createRecruitmentCycle(recruitmentCycle);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期创建成功", createdCycle));
        } catch (BusinessException e) {
            logger.warn("创建招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("创建招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期创建失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 更新招募周期（仅管理员）
     */
    @PutMapping
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> updateRecruitmentCycle(@RequestBody RecruitmentCycle recruitmentCycle) {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser();
            checkAdminPermission(currentUser);
            
            logger.info("管理员{}更新招募周期，ID: {}", currentUser.getUsername(), recruitmentCycle.getCycleId());
            RecruitmentCycle updatedCycle = recruitmentCycleService.updateRecruitmentCycle(recruitmentCycle);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期更新成功", updatedCycle));
        } catch (BusinessException e) {
            logger.warn("更新招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("更新招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期更新失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 删除招募周期（仅管理员）
     */
    @DeleteMapping("/{cycleId}")
    public ResponseEntity<ResponseMessage<String>> deleteRecruitmentCycle(@PathVariable Integer cycleId) {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser();
            checkAdminPermission(currentUser);
            
            logger.info("管理员{}删除招募周期，ID: {}", currentUser.getUsername(), cycleId);
            recruitmentCycleService.deleteRecruitmentCycle(cycleId);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期删除成功", "招募周期删除成功"));
        } catch (BusinessException e) {
            logger.warn("删除招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("删除招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期删除失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据ID获取招募周期
     */
    @GetMapping("/{cycleId}")
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> getRecruitmentCycleById(@PathVariable Integer cycleId) {
        try {
            logger.debug("获取招募周期，ID: {}", cycleId);
            RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
            if (cycle == null) {
                logger.warn("招募周期不存在，ID: {}", cycleId);
                throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
            }
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期成功", cycle));
        } catch (BusinessException e) {
            logger.warn("获取招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 获取所有招募周期
     */
    @GetMapping
    public ResponseEntity<ResponseMessage<List<RecruitmentCycle>>> getAllRecruitmentCycles() {
        try {
            logger.debug("获取所有招募周期");
            List<RecruitmentCycle> cycles = recruitmentCycleService.getAllRecruitmentCycles();
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", cycles));
        } catch (BusinessException e) {
            logger.warn("获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据状态获取招募周期
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ResponseMessage<List<RecruitmentCycle>>> getRecruitmentCyclesByStatus(@PathVariable Integer status) {
        try {
            logger.debug("根据状态获取招募周期，状态: {}", status);
            List<RecruitmentCycle> cycles = recruitmentCycleService.getRecruitmentCyclesByStatus(status);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", cycles));
        } catch (BusinessException e) {
            logger.warn("根据状态获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据状态获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据是否启用获取招募周期
     */
    @GetMapping("/active/{isActive}")
    public ResponseEntity<ResponseMessage<List<RecruitmentCycle>>> getRecruitmentCyclesByIsActive(@PathVariable Integer isActive) {
        try {
            logger.debug("根据是否启用获取招募周期，是否启用: {}", isActive);
            List<RecruitmentCycle> cycles = recruitmentCycleService.getRecruitmentCyclesByIsActive(isActive);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", cycles));
        } catch (BusinessException e) {
            logger.warn("根据是否启用获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据是否启用获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据学年获取招募周期
     */
    @GetMapping("/academic-year/{academicYear}")
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> getRecruitmentCycleByAcademicYear(@PathVariable String academicYear) {
        try {
            logger.debug("根据学年获取招募周期，学年: {}", academicYear);
            RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleByAcademicYear(academicYear);
            if (cycle == null) {
                logger.warn("招募周期不存在，学年: {}", academicYear);
                throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
            }
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期成功", cycle));
        } catch (BusinessException e) {
            logger.warn("根据学年获取招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据学年获取招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 从请求头获取JWT令牌
     */
    private String getTokenFromHeader() {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 验证JWT令牌并获取用户信息
     */
    private User getCurrentUser() {
        String token = getTokenFromHeader();
        if (token == null) {
            throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
        }
        
        try {
            // 验证令牌并获取用户名
            String username = jwtTokenUtil.extractUsername(token);
            if (!jwtTokenUtil.validateToken(token, username)) {
                throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
            }
            
            // 获取用户信息
            User user = userService.getUserByUsername(username);
            if (user == null) {
                throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
            }
            
            return user;
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
        }
    }
    
    /**
     * 验证管理员权限
     */
    private void checkAdminPermission(User user) {
        if (!User.ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
        }
    }
}