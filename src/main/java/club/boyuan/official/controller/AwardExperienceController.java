package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IAwardExperienceService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 奖项经验控制器
 * 处理用户奖项经验的增删改查操作
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/awards")
public class AwardExperienceController {

    private final IAwardExperienceService awardExperienceService;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 创建获奖经历
     * @param awardExperience 奖项经验信息
     * @param request HTTP请求
     * @return 创建结果
     */
    @PostMapping
    public ResponseEntity<ResponseMessage<?>> createAward(@RequestBody AwardExperience awardExperience, HttpServletRequest request) {
        try {
            // 获取当前登录用户信息
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            String token = authHeader.substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
            } catch (Exception e) { 
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            
            // 获取目标用户ID
            Integer targetUserId = awardExperience.getUserId();

            // 权限控制：
            if (targetUserId == null) {
                // 未指定用户ID - 拒绝访问
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            } else if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
                // 管理员：验证目标用户是否存在
                User targetUser = userService.getUserById(targetUserId);
                if (targetUser == null) {
                    throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
                }
            } else {
                // 普通用户：只能为自己创建获奖经历
                if (!targetUserId.equals(currentUser.getUserId())) {
                    throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
                }
            }

            // 设置获奖经历的用户ID
            awardExperience.setUserId(targetUserId);
            
            AwardExperience createdAward = awardExperienceService.create(awardExperience);
            Map<String, Integer> data = new HashMap<>();
            data.put("award_id", createdAward.getAwardId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ResponseMessage.success(data));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取获奖经历
     * @param id 奖项ID
     * @param request HTTP请求
     * @return 奖项信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseMessage<?>> getAwardById(@PathVariable Integer id, HttpServletRequest request) {
        try {
            // 获取当前登录用户信息
            String token = request.getHeader("Authorization").substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
            } catch (Exception e) { 
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User currentUser = userService.getUserByUsername(username);
            
            AwardExperience award = awardExperienceService.getById(id);
            if (award == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseMessage.error(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getCode(), BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getMessage()));
            }
            
            // 权限检查：管理员可以查看所有，普通用户只能查看自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                if (!award.getUserId().equals(currentUser.getUserId())) {
                    throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
                }
            }
            
            return ResponseEntity.ok(ResponseMessage.success(award));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 根据用户ID获取所有获奖经历
     * @param userId 用户ID
     * @param request HTTP请求
     * @return 奖项列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ResponseMessage<?>> getAwardsByUserId(@PathVariable Integer userId, HttpServletRequest request) {
        try {
            // 获取当前登录用户信息
            String token = request.getHeader("Authorization").substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
            } catch (Exception e) { 
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User currentUser = userService.getUserByUsername(username);
            
            // 权限检查：管理员可以查看所有，普通用户只能查看自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                if (!userId.equals(currentUser.getUserId())) {
                    throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
                }
            }
            
            List<AwardExperience> awards = awardExperienceService.getByUserId(userId);
            return ResponseEntity.ok(ResponseMessage.success(awards));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 更新获奖经历
     * @param awardExperience 奖项经验信息
     * @param request HTTP请求
     * @return 更新结果
     */
    @PutMapping
    public ResponseEntity<ResponseMessage<?>> updateAward(@RequestBody AwardExperience awardExperience, HttpServletRequest request) {
        try {
            // 从JWT令牌获取当前登录用户ID
            String token = request.getHeader("Authorization").substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
            } catch (Exception e) { 
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User user = userService.getUserByUsername(username);
            Integer currentUserId = user.getUserId();
            User currentUser = userService.getUserByUsername(username);
            
            // 获取要更新的获奖经历原始数据
            AwardExperience originalAward = awardExperienceService.getById(awardExperience.getAwardId());
            if (originalAward == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseMessage.error(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getCode(), BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getMessage()));
            }
            
            // 权限检查：管理员可以修改所有人的，普通用户只能修改自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                if (!originalAward.getUserId().equals(currentUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ResponseMessage.error(BusinessExceptionEnum.PERMISSION_DENIED.getCode(), BusinessExceptionEnum.PERMISSION_DENIED.getMessage()));
                }
            }
            
            // 设置用户ID为当前登录用户ID，防止篡改
            awardExperience.setUserId(currentUserId);
            AwardExperience updatedAward = awardExperienceService.update(awardExperience);
            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 删除获奖经历
     * @param id 奖项ID
     * @param request HTTP请求
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseMessage<?>> deleteAward(@PathVariable Integer id, HttpServletRequest request) {
        try {
            // 获取当前登录用户信息
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            String token = authHeader.substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }

            // 获取要删除的获奖经历
            AwardExperience award = awardExperienceService.getById(id);
            if (award == null) {
                throw new BusinessException(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND);
            }

            // 权限检查：管理员可以删除所有，普通用户只能删除自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !award.getUserId().equals(currentUser.getUserId())) {
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
            }

            awardExperienceService.deleteById(id);
            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
}