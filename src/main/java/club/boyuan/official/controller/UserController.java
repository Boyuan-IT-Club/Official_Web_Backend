package club.boyuan.official.controller;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IAwardExperienceService;
import club.boyuan.official.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import club.boyuan.official.utils.JwtTokenUtil;
import club.boyuan.official.utils.FileUploadUtil;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import club.boyuan.official.utils.JwtTokenUtil;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static club.boyuan.official.utils.FileUploadUtil.generateFullHttpPath;

@RestController //接口方法返回对象转换成Json文本
@RequestMapping("api/user")
@AllArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final IUserService userService;

    private final HttpServletRequest request;

    private final JwtTokenUtil jwtTokenUtil;

    private final IAwardExperienceService awardExperienceService;

    /**
     * 上传用户头像
     */
    @PostMapping("/avatar")
    public ResponseEntity<ResponseMessage> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件是否为空
            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseMessage(400, "上传文件为空", null));
            }
            
            // 获取当前用户
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            
            // 上传文件并获取路径（使用新的通用方法）
            String avatarPath = FileUploadUtil.uploadFile(file, "avatars/", "image/");
            
            // 生成完整HTTP路径
            String fullHttpPath = generateFullHttpPath(avatarPath, request.getServerName(), request.getServerPort());
            
            // 更新用户头像信息，使用新的专门方法避免密码被修改
            User updatedUser = userService.updateAvatar(userId, avatarPath);
            
            Map<String, String> responseData = new HashMap<>();
            responseData.put("avatar", avatarPath);
            responseData.put("fullHttpPath", fullHttpPath);
            
            logger.info("用户ID为{}的用户成功上传头像，路径为{}", userId, avatarPath);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (IOException e) {
            logger.error("头像上传失败: 文件操作异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "头像上传失败: " + e.getMessage()));
        } catch (BusinessException e) {
            logger.error("头像上传失败: 业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("头像上传失败: 未知错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "头像上传失败: " + e.getMessage()));
        }
    }
    
    /**
     * 通用文件上传接口
     * @param file 上传的文件
     * @param uploadPath 上传路径
     * @return 文件存储路径
     */
    @PostMapping("/upload")
    public ResponseEntity<ResponseMessage> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadPath") String uploadPath,
            HttpServletRequest request) {
        try {
            // 获取当前用户
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            Integer userId = getAuthenticatedUserId();
            logger.info("用户{}尝试上传文件到路径{}", username, uploadPath);
            
            // 上传文件并获取路径
            String filePath = FileUploadUtil.uploadFile(file, uploadPath);
            
            // 生成完整HTTP路径
            String fullHttpPath = generateFullHttpPath(filePath, request.getServerName(), request.getServerPort());
            
            Map<String, String> responseData = new HashMap<>();
            responseData.put("filePath", filePath);
            responseData.put("fullHttpPath", fullHttpPath);
            
            logger.info("用户{}成功上传文件，路径为{}", username, filePath);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("文件上传失败，用户: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "文件上传失败: " + e.getMessage()));
        }
    }
    
    /**
     * 带文件类型验证的文件上传接口
     * @param file 上传的文件
     * @param uploadPath 上传路径
     * @param fileType 文件类型（如"image/"、"application/pdf"等）
     * @return 文件存储路径
     */
    @PostMapping("/upload/typed")
    public ResponseEntity<ResponseMessage> uploadTypedFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadPath") String uploadPath,
            @RequestParam("fileType") String fileType,
            HttpServletRequest request) {
        try {
            // 获取当前用户
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            Integer userId = getAuthenticatedUserId();
            logger.info("用户{}尝试上传{}类型的文件到路径{}", username, fileType, uploadPath);
            
            // 上传文件并获取路径
            String filePath = FileUploadUtil.uploadFile(file, uploadPath, fileType);
            
            // 生成完整HTTP路径
            String fullHttpPath = generateFullHttpPath(filePath, request.getServerName(), request.getServerPort());
            
            Map<String, String> responseData = new HashMap<>();
            responseData.put("filePath", filePath);
            responseData.put("fullHttpPath", fullHttpPath);
            
            logger.info("用户{}成功上传{}类型的文件，路径为{}", username, fileType, filePath);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (Exception e) {
            String username = "unknown";
            if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
                try {
                    username = jwtTokenUtil.extractUsername(request.getHeader("Authorization").substring(7));
                } catch (Exception ex) {
                    logger.warn("无法从token中提取用户名");
                }
            }
            
            logger.error("文件上传失败，用户: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "文件上传失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取当前用户信息，返回响应实体
     */
    @GetMapping("/me")
    public ResponseEntity<ResponseMessage> getCurrentUser() {
        try {
            // 在实际项目中，应该从token中解析用户ID
            // 从认证信息中获取用户ID（实际项目中实现）
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            List<AwardExperience> awardExperiences = awardExperienceService.getByUserId(userId);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", user);
            responseData.put("awardExperiences", awardExperiences);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 获取当前登录用户信息，返回用户对象
     */
    private User getCurrentUserEntity() {
        Integer userId = getAuthenticatedUserId();
        return userService.getUserById(userId);
    }

    /**
     * 更新当前用户信息（从token获取ID，支持部分字段更新）
     */
    @PutMapping("/me")
    public ResponseEntity<ResponseMessage> updateCurrentUser(@RequestBody Map<String, Object> userInfo) {
        try {
            // 从JWT令牌获取用户ID
            Integer userId = getAuthenticatedUserId();
            User existingUser = userService.getUserById(userId);
            
            logger.info("用户ID为{}的用户开始更新个人信息", userId);
            logger.debug("接收到的更新信息: {}", userInfo);

            // 仅更新传入的非空字段，防止修改敏感字段
            if (userInfo.containsKey("username") && userInfo.get("username") != null) {
                existingUser.setUsername((String) userInfo.get("username"));
                logger.debug("更新用户名为: {}", userInfo.get("username"));
            }
            // 移除密码更新逻辑，防止通过此接口更新密码
            if (userInfo.containsKey("password") && userInfo.get("password") != null) {
                logger.warn("尝试通过/me接口更新密码，操作已被阻止，用户ID: {}", userId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "不能通过此接口更新密码"));
            }
            if (userInfo.containsKey("email") && userInfo.get("email") != null) {
                existingUser.setEmail((String) userInfo.get("email"));
                logger.debug("更新邮箱为: {}", userInfo.get("email"));
            }
            if (userInfo.containsKey("name") && userInfo.get("name") != null) {
                existingUser.setName((String) userInfo.get("name"));
                logger.debug("更新姓名为: {}", userInfo.get("name"));
            }
            if (userInfo.containsKey("phone") && userInfo.get("phone") != null) {
                existingUser.setPhone((String) userInfo.get("phone"));
                logger.debug("更新电话为: {}", userInfo.get("phone"));
            }
            if (userInfo.containsKey("dept") && userInfo.get("dept") != null) {
                existingUser.setDept((String) userInfo.get("dept"));
                logger.debug("更新部门为: {}", userInfo.get("dept"));
            }
            // 不允许通过此接口更新status和isMember字段
            // if (userInfo.containsKey("status")) {
            //     existingUser.setStatus((Boolean) userInfo.get("status"));
            // }
            // if (userInfo.containsKey("isMember")) {
            //     existingUser.setIsMember((Boolean) userInfo.get("isMember"));
            // }

            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(existingUser, userDTO);
            // 确保不会通过DTO更新密码
            userDTO.setPassword(null);
            userService.edit(userDTO);
            
            logger.info("用户ID为{}的用户个人信息更新成功", userId);

            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            logger.error("更新用户信息时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新用户信息时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
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
     * 验证JWT令牌并获取用户ID
     */
    private Integer getAuthenticatedUserId() {
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

            return user.getUserId();
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
        }
    }


    // 验证管理员权限
    private void checkAdminPermission(User user) {
        if (!User.ROLE_ADMIN.equals(user.getRole())) {
            throw new IllegalArgumentException("操作权限不足，需要管理员权限");
        }
    }

    //Restful风格
    //增加
    @PostMapping
      public ResponseEntity<ResponseMessage<User>> add(@Validated @RequestBody UserDTO user){
        try {
            logger.debug("收到添加用户请求: {}", user.toString());
            User userNew = userService.add(user);
            return ResponseEntity.ok(ResponseMessage.success(userNew));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }
    //查询

    @GetMapping("/{userId}")
    public ResponseEntity<ResponseMessage<?>> get(@PathVariable Integer userId) {
        try {
            User currentUser = getCurrentUserEntity();
            // 管理员可以查看所有用户，普通用户只能查看自己
            if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(userId)) {
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            User userNew = userService.getUserById(userId);
            if (userNew == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            List<AwardExperience> awardExperiences = awardExperienceService.getByUserId(userId);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", userNew);
            responseData.put("awardExperiences", awardExperiences);
            logger.debug("成功获取用户ID为{}的奖项经验信息", userId);
            return ResponseEntity.ok(new ResponseMessage(200, "success", responseData));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }

    //删除
    @DeleteMapping("/{userId}")
    public ResponseEntity<ResponseMessage<Void>> delete(@PathVariable Integer userId) {
        try {
            userService.deleteUserById(userId);
            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }

    /**
     * 更新指定用户信息（需传入ID，支持部分字段更新）
     */
    @PutMapping
    public ResponseEntity<ResponseMessage> edit(@RequestBody Map<String, Object> userInfo) {
        try {
            User currentUser = getCurrentUserEntity();
            
            logger.info("用户ID为{}开始更新用户信息", currentUser.getUserId());
            logger.debug("接收到的更新信息: {}", userInfo);
            
            // 从请求体获取userId
            Integer targetUserId = (Integer) userInfo.get("userId");
            if (targetUserId == null) {
                logger.warn("缺少必需的userId字段");
                throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
            }

            // 管理员可以更新所有用户，普通用户只能更新自己
            if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(targetUserId)) {
                logger.warn("用户ID为{}的用户尝试更新其他用户信息，权限不足", currentUser.getUserId());
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }

            User existingUser = userService.getUserById(targetUserId);
            // 移除userId，避免更新用户ID
            userInfo.remove("userId");
            if (existingUser == null) {
                logger.warn("尝试更新不存在的用户，用户ID: {}", targetUserId);
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }

            // 仅更新传入的非空字段
            if (userInfo.containsKey("username") && userInfo.get("username") != null) {
                existingUser.setUsername((String) userInfo.get("username"));
                logger.debug("更新用户名为: {}", userInfo.get("username"));
            }
            // 只有管理员可以通过此接口修改密码
            if (userInfo.containsKey("password") && userInfo.get("password") != null) {
                if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
                    existingUser.setPassword((String) userInfo.get("password"));
                    logger.debug("管理员更新用户密码，目标用户ID: {}", targetUserId);
                } else {
                    logger.warn("非管理员用户尝试更新密码，用户ID: {}", currentUser.getUserId());
                }
            }
            if (userInfo.containsKey("email") && userInfo.get("email") != null) {
                existingUser.setEmail((String) userInfo.get("email"));
                logger.debug("更新邮箱为: {}", userInfo.get("email"));
            }
            if (userInfo.containsKey("role") && userInfo.get("role") != null) {
                // 只有管理员可以修改角色
                if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
                    existingUser.setRole((String) userInfo.get("role"));
                    logger.debug("更新角色为: {}", userInfo.get("role"));
                } else {
                    logger.warn("非管理员用户尝试更新角色，用户ID: {}", currentUser.getUserId());
                    throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
                }
            }
            if (userInfo.containsKey("name") && userInfo.get("name") != null) {
                existingUser.setName((String) userInfo.get("name"));
                logger.debug("更新姓名为: {}", userInfo.get("name"));
            }
            if (userInfo.containsKey("phone") && userInfo.get("phone") != null) {
                existingUser.setPhone((String) userInfo.get("phone"));
                logger.debug("更新电话为: {}", userInfo.get("phone"));
            }
            if (userInfo.containsKey("dept") && userInfo.get("dept") != null) {
                existingUser.setDept((String) userInfo.get("dept"));
                logger.debug("更新部门为: {}", userInfo.get("dept"));
            }
            if (userInfo.containsKey("status") && userInfo.get("status") != null) {
                existingUser.setStatus((Boolean) userInfo.get("status"));
                logger.debug("更新状态为: {}", userInfo.get("status"));
            }
            if (userInfo.containsKey("isMember") && userInfo.get("isMember") != null) {
                existingUser.setIsMember((Boolean) userInfo.get("isMember"));
                logger.debug("更新会员状态为: {}", userInfo.get("isMember"));
            }

            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(existingUser, userDTO);
            userService.edit(userDTO);
            
            logger.info("用户ID为{}的用户信息更新成功，操作者用户ID: {}", targetUserId, currentUser.getUserId());

            return ResponseEntity.ok(ResponseMessage.success());
        } catch (BusinessException e) {
            logger.error("更新用户信息时发生业务异常", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新用户信息时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
}