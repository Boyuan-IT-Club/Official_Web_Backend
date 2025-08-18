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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AwardExperienceController.class);

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
            logger.info("开始创建获奖经历");
            logger.debug("接收到的获奖经历信息: {}", awardExperience);
            
            // 获取当前登录用户信息
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("认证失败：缺少Authorization头或格式不正确");
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            String token = authHeader.substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
                logger.debug("从token中提取用户名: {}", username);
            } catch (Exception e) { 
                logger.error("解析token时发生异常", e);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                logger.warn("未找到用户: {}", username);
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            
            // 获取目标用户ID
            Integer targetUserId = awardExperience.getUserId();

            // 权限控制：
            if (targetUserId == null) {
                // 未指定用户ID - 拒绝访问
                logger.warn("创建获奖经历时未指定用户ID，操作者用户ID: {}", currentUser.getUserId());
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            } else if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
                // 管理员：验证目标用户是否存在
                User targetUser = userService.getUserById(targetUserId);
                if (targetUser == null) {
                    logger.warn("管理员尝试为不存在的用户创建获奖经历，目标用户ID: {}", targetUserId);
                    throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
                }
                logger.debug("管理员为用户ID为{}的用户创建获奖经历", targetUserId);
            } else {
                // 普通用户：只能为自己创建获奖经历
                if (!targetUserId.equals(currentUser.getUserId())) {
                    logger.warn("用户ID为{}的用户尝试为其他用户创建获奖经历，目标用户ID: {}", currentUser.getUserId(), targetUserId);
                    throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
                }
                logger.debug("用户为自己创建获奖经历，用户ID: {}", currentUser.getUserId());
            }

            // 设置获奖经历的用户ID
            awardExperience.setUserId(targetUserId);
            
            AwardExperience createdAward = awardExperienceService.create(awardExperience);
            logger.info("成功创建获奖经历，获奖ID: {}, 用户ID: {}", createdAward.getAwardId(), targetUserId);
            
            Map<String, Integer> data = new HashMap<>();
            data.put("award_id", createdAward.getAwardId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ResponseMessage.success(data));
        } catch (BusinessException e) {
            logger.error("创建获奖经历时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("创建获奖经历时发生系统异常", e);
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
            logger.info("开始获取获奖经历，获奖ID: {}", id);
            
            // 获取当前登录用户信息
            String token = request.getHeader("Authorization").substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
                logger.debug("从token中提取用户名: {}", username);
            } catch (Exception e) { 
                logger.error("解析token时发生异常", e);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User currentUser = userService.getUserByUsername(username);
            
            AwardExperience award = awardExperienceService.getById(id);
            if (award == null) {
                logger.warn("未找到指定的获奖经历，获奖ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseMessage.error(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getCode(), BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getMessage()));
            }
            
            // 权限检查：管理员可以查看所有，普通用户只能查看自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                if (!award.getUserId().equals(currentUser.getUserId())) {
                    logger.warn("用户ID为{}的用户尝试查看其他用户的获奖经历，目标用户ID: {}, 获奖ID: {}", 
                               currentUser.getUserId(), award.getUserId(), id);
                    throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
                }
            }
            
            logger.info("成功获取获奖经历，获奖ID: {}", id);
            return ResponseEntity.ok(ResponseMessage.success(award));
        } catch (BusinessException e) {
            logger.error("获取获奖经历时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取获奖经历时发生系统异常", e);
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
            logger.info("开始获取用户的所有获奖经历，用户ID: {}", userId);
            
            // 获取当前登录用户信息
            String token = request.getHeader("Authorization").substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
                logger.debug("从token中提取用户名: {}", username);
            } catch (Exception e) { 
                logger.error("解析token时发生异常", e);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User currentUser = userService.getUserByUsername(username);
            
            // 权限检查：管理员可以查看所有，普通用户只能查看自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                if (!userId.equals(currentUser.getUserId())) {
                    logger.warn("用户ID为{}的用户尝试查看其他用户的获奖经历，目标用户ID: {}", currentUser.getUserId(), userId);
                    throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
                }
            }
            
            List<AwardExperience> awards = awardExperienceService.getByUserId(userId);
            logger.info("成功获取用户的所有获奖经历，用户ID: {}, 获奖经历数量: {}", userId, awards.size());
            return ResponseEntity.ok(ResponseMessage.success(awards));
        } catch (BusinessException e) {
            logger.error("获取用户获奖经历时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取用户获奖经历时发生系统异常", e);
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
            logger.info("开始更新获奖经历，获奖ID: {}", awardExperience.getAwardId());
            logger.debug("接收到的获奖经历信息: {}", awardExperience);
            
            // 从JWT令牌获取当前登录用户ID
            String token = request.getHeader("Authorization").substring(7);
            String username; 
            try { 
                username = jwtTokenUtil.extractUsername(token); 
                logger.debug("从token中提取用户名: {}", username);
            } catch (Exception e) { 
                logger.error("解析token时发生异常", e);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED); 
            }
            User user = userService.getUserByUsername(username);
            Integer currentUserId = user.getUserId();
            User currentUser = userService.getUserByUsername(username);
            
            // 获取要更新的获奖经历原始数据
            AwardExperience originalAward = awardExperienceService.getById(awardExperience.getAwardId());
            if (originalAward == null) {
                logger.warn("尝试更新不存在的获奖经历，获奖ID: {}", awardExperience.getAwardId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseMessage.error(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getCode(), BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND.getMessage()));
            }
            
            // 权限检查：管理员可以修改所有人的，普通用户只能修改自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                if (!originalAward.getUserId().equals(currentUserId)) {
                    logger.warn("用户ID为{}的用户尝试更新其他用户的获奖经历，目标用户ID: {}, 获奖ID: {}", 
                               currentUserId, originalAward.getUserId(), awardExperience.getAwardId());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ResponseMessage.error(BusinessExceptionEnum.PERMISSION_DENIED.getCode(), BusinessExceptionEnum.PERMISSION_DENIED.getMessage()));
                }
            }
            
            // 设置用户ID为当前登录用户ID，防止篡改
            awardExperience.setUserId(currentUserId);
            AwardExperience updatedAward = awardExperienceService.update(awardExperience);
            logger.info("成功更新获奖经历，获奖ID: {}", awardExperience.getAwardId());
            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            logger.error("更新获奖经历时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新获奖经历时发生系统异常", e);
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
            logger.info("开始删除获奖经历，获奖ID: {}", id);
            
            // 获取当前登录用户信息
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("认证失败：缺少Authorization头或格式不正确");
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            String token = authHeader.substring(7);
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                logger.warn("未找到用户: {}", username);
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }

            // 获取要删除的获奖经历
            AwardExperience award = awardExperienceService.getById(id);
            if (award == null) {
                logger.warn("尝试删除不存在的获奖经历，获奖ID: {}", id);
                throw new BusinessException(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND);
            }

            // 权限检查：管理员可以删除所有，普通用户只能删除自己的
            if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !award.getUserId().equals(currentUser.getUserId())) {
                logger.warn("用户ID为{}的用户尝试删除其他用户的获奖经历，目标用户ID: {}, 获奖ID: {}", 
                           currentUser.getUserId(), award.getUserId(), id);
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
            }

            awardExperienceService.deleteById(id);
            logger.info("成功删除获奖经历，获奖ID: {}", id);
            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            logger.error("删除获奖经历时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("删除获奖经历时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
}