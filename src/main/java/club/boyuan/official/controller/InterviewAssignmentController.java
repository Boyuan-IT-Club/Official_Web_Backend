package club.boyuan.official.controller;

import club.boyuan.official.dto.InterviewAssignmentResultDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IInterviewAssignmentService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 面试时间分配控制器
 */
@RestController
@RequestMapping("/api/interviews")
@AllArgsConstructor
public class InterviewAssignmentController {
    
    private static final Logger logger = LoggerFactory.getLogger(InterviewAssignmentController.class);
    
    private final IInterviewAssignmentService interviewAssignmentService;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    
    /**
     * 为指定招募周期自动分配面试时间（仅管理员）
     * @param cycleId 招募周期ID
     * @param request HTTP请求
     * @return 面试时间分配结果
     */
    @PostMapping("/assign/{cycleId}")
    public ResponseEntity<ResponseMessage<InterviewAssignmentResultDTO>> assignInterviews(
            @PathVariable Integer cycleId,
            HttpServletRequest request) {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser(request);
            checkAdminPermission(currentUser);
            
            logger.info("管理员 {} 开始为招募周期 ID {} 分配面试时间", currentUser.getUsername(), cycleId);
            
            // 执行面试时间分配
            InterviewAssignmentResultDTO result = interviewAssignmentService.assignInterviews(cycleId);
            
            logger.info("面试时间分配完成，已分配 {} 人，未分配 {} 人", 
                    result.getAssignedInterviews().size(), result.getUnassignedUsers().size());
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "面试时间分配成功", result));
        } catch (BusinessException e) {
            logger.warn("面试时间分配业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("面试时间分配系统异常，招募周期ID: {}", cycleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "面试时间分配失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 从请求中获取当前用户信息
     */
    private User getCurrentUser(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
        }
        
        try {
            String token = bearerToken.substring(7);
            String username = jwtTokenUtil.extractUsername(token);
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