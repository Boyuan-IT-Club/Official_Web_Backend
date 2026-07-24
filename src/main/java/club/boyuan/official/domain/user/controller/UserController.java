package club.boyuan.official.domain.user.controller;
import club.boyuan.official.common.converter.UserConverter;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.user.dto.UserDTO;
import club.boyuan.official.persistence.entity.AwardExperience;
import club.boyuan.official.persistence.entity.Role;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.IAwardExperienceService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.ExcelExportUtil;
import club.boyuan.official.common.utils.FileUploadUtil;
import club.boyuan.official.common.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static club.boyuan.official.common.utils.FileUploadUtil.generateFullHttpPath;

@RestController //接口方法返回对象转换成Json文本
@RequestMapping("/api/user")
@AllArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final IUserService userService;

    private final HttpServletRequest request;

    private final IAwardExperienceService awardExperienceService;

    private final UserConverter userConverter;

    /**
     * 上传用户头像
     */
    @PostMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件是否为空
            if (file == null || file.isEmpty()) {
                logger.warn("头像上传失败: 上传文件为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseMessage<>(400, "上传文件为空", null));
            }
            
            // 获取当前用户
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            
            // 保存旧头像路径
            String oldAvatarPath = user.getAvatar();
            
            // 上传文件并获取路径（使用新的通用方法）
            String avatarPath = FileUploadUtil.uploadFile(file, "avatars/", "image/");
            
            // 生成完整HTTP路径
            String fullHttpPath = generateFullHttpPath(avatarPath, request.getServerName(), request.getServerPort());
            
            // 更新用户头像信息，使用新的专门方法避免密码被修改
            User updatedUser = userService.updateAvatar(userId, avatarPath);
            
            // 删除旧头像文件（如果有）
            if (oldAvatarPath != null && !oldAvatarPath.isEmpty()) {
                try {
                    Path oldAvatarFile = Paths.get(oldAvatarPath.substring(1)); // 去掉开头的 "/"
                    Files.deleteIfExists(oldAvatarFile);
                    logger.info("成功删除旧头像文件: {}", oldAvatarPath);
                } catch (IOException e) {
                    logger.warn("删除旧头像文件失败: {}", oldAvatarPath, e);
                }
            }
            
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadPath") String uploadPath) {
        try {
            // 获取当前用户
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            logger.info("用户{}({})尝试上传文件到路径{}", user.getUsername(), userId, uploadPath);
            
            // 上传文件并获取路径
            String filePath = FileUploadUtil.uploadFile(file, uploadPath);
            
            // 生成完整HTTP路径
            String fullHttpPath = generateFullHttpPath(filePath, request.getServerName(), request.getServerPort());
            
            Map<String, String> responseData = new HashMap<>();
            responseData.put("filePath", filePath);
            responseData.put("fullHttpPath", fullHttpPath);
            
            logger.info("用户{}({})成功上传文件，路径为{}", user.getUsername(), userId, filePath);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (Exception e) {
            Integer userId = null;
            String username = "unknown";
            try {
                userId = getAuthenticatedUserId();
                User user = userService.getUserById(userId);
                username = user.getUsername();
            } catch (Exception ex) {
                logger.warn("无法获取当前用户信息");
            }
            
            logger.error("文件上传失败，用户: {}({})", username, userId, e);
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> uploadTypedFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadPath") String uploadPath,
            @RequestParam("fileType") String fileType) {
        try {
            // 获取当前用户
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            logger.info("用户{}({})尝试上传{}类型的文件到路径{}", user.getUsername(), userId, fileType, uploadPath);
            
            // 上传文件并获取路径
            String filePath = FileUploadUtil.uploadFile(file, uploadPath, fileType);
            
            // 生成完整HTTP路径
            String fullHttpPath = generateFullHttpPath(filePath, request.getServerName(), request.getServerPort());
            
            Map<String, String> responseData = new HashMap<>();
            responseData.put("filePath", filePath);
            responseData.put("fullHttpPath", fullHttpPath);
            
            logger.info("用户{}({})成功上传{}类型的文件，路径为{}", user.getUsername(), userId, fileType, filePath);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (Exception e) {
            Integer userId = null;
            String username = "unknown";
            try {
                userId = getAuthenticatedUserId();
                User user = userService.getUserById(userId);
                username = user.getUsername();
            } catch (Exception ex) {
                logger.warn("无法获取当前用户信息");
            }
            
            logger.error("文件上传失败，用户: {}({})", username, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "文件上传失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> getCurrentUser() {
        try {
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            List<AwardExperience> awardExperiences = awardExperienceService.getByUserId(userId);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", user);
            responseData.put("awardExperiences", awardExperiences);
            logger.info("成功获取用户ID为{}的用户信息", userId);
            return ResponseEntity.ok(ResponseMessage.success(responseData));
        } catch (BusinessException e) {
            logger.warn("获取当前用户信息业务异常: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取当前用户信息系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    private User getCurrentUserEntity() {
        Integer userId = getAuthenticatedUserId();
        return userService.getUserById(userId);
    }

    /**
     * 更新当前用户信息（从token获取ID，支持部分字段更新）
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> updateCurrentUser(@RequestBody Map<String, Object> userInfo) {
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
            if (userInfo.containsKey("major") && userInfo.get("major") != null) {
                existingUser.setMajor((String) userInfo.get("major"));
                logger.debug("更新专业为: {}", userInfo.get("major"));
            }
            if (userInfo.containsKey("github") && userInfo.get("github") != null) {
                existingUser.setGithub((String) userInfo.get("github"));
                logger.debug("更新GitHub地址为: {}", userInfo.get("github"));
            }
            if (userInfo.containsKey("deptId") && userInfo.get("deptId") != null) {
                existingUser.setDeptId((Integer) userInfo.get("deptId"));
                logger.debug("更新部门ID为: {}", userInfo.get("deptId"));
            }
            // 不允许通过此接口更新status和isMember字段
            // if (userInfo.containsKey("status")) {
            //     existingUser.setStatus((Boolean) userInfo.get("status"));
            // }
            // if (userInfo.containsKey("isMember")) {
            //     existingUser.setIsMember((Boolean) userInfo.get("isMember"));
            // }

            UserDTO userDTO = userConverter.toDto(existingUser);
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
     * 导出用户数据为Excel格式（仅管理员可用）。
     * 认证与管理员鉴权由 {@link PreAuthorize} + JwtAuthenticationFilter 统一保证，此处不再手动校验令牌。
     * @param response HTTP响应
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/export/excel")
    public void exportUsersToExcel(HttpServletResponse response) {
        try {
            User currentUser = getCurrentUserEntity();

            // 获取所有用户
            List<User> users = userService.getAllUsers(currentUser);
            if (users == null || users.isEmpty()) {
                logger.warn("管理员{}尝试导出用户数据，但用户列表为空", currentUser.getUsername());
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "没有可导出的用户数据");
                return;
            }

            // 生成Excel
            byte[] excelBytes = ExcelExportUtil.exportUsersToExcel(users);

            // 生成带时间戳的文件名，并进行URL编码
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = URLEncoder.encode("用户列表_" + timestamp + ".xlsx", StandardCharsets.UTF_8);

            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            response.setContentLength(excelBytes.length);

            // 写入响应
            response.getOutputStream().write(excelBytes);
            response.getOutputStream().flush();

            logger.info("管理员{}成功导出{}个用户为Excel", currentUser.getUsername(), users.size());
        } catch (Exception e) {
            logger.error("导出用户为Excel失败", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 获取当前登录用户ID。
     * 令牌解析与校验已由 JwtAuthenticationFilter 完成，这里只从 SecurityContext 读取用户名并查用户。
     */
    private Integer getAuthenticatedUserId() {
        String username = SecurityUtil.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
        }
        return user.getUserId();
    }


    // 验证管理员权限
    private boolean hasAdminRole(User user) {
        if (user.getRoles() == null) {
            return false;
        }
        for (Role role : user.getRoles()) {
            if ("ROLE_ADMIN".equals(role.getRoleName())) {
                return true;
            }
        }
        return false;
    }

    //Restful风格
    //增加
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> get(@PathVariable Integer userId) {
        try {
            User currentUser = getCurrentUserEntity();
            // 管理员可以查看所有用户，普通用户只能查看自己
            if (!hasAdminRole(currentUser) && !currentUser.getUserId().equals(userId)) {
                logger.warn("用户ID为{}的用户尝试查看其他用户信息，权限不足", currentUser.getUserId());
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
            return ResponseEntity.ok(new ResponseMessage<>(200, "success", responseData));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }

    //删除
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> edit(@RequestBody Map<String, Object> userInfo) {
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
            if (!hasAdminRole(currentUser) && !currentUser.getUserId().equals(targetUserId)) {
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
                if (hasAdminRole(currentUser)) {
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
            // 移除旧的role字段处理，角色现在通过user_role表管理
            if (userInfo.containsKey("role")) {
                logger.warn("尝试更新废弃的role字段，用户ID: {}", currentUser.getUserId());
                userInfo.remove("role");
            }
            if (userInfo.containsKey("name") && userInfo.get("name") != null) {
                existingUser.setName((String) userInfo.get("name"));
                logger.debug("更新姓名为: {}", userInfo.get("name"));
            }
            if (userInfo.containsKey("phone") && userInfo.get("phone") != null) {
                existingUser.setPhone((String) userInfo.get("phone"));
                logger.debug("更新电话为: {}", userInfo.get("phone"));
            }
            if (userInfo.containsKey("major") && userInfo.get("major") != null) {
                existingUser.setMajor((String) userInfo.get("major"));
                logger.debug("更新专业为: {}", userInfo.get("major"));
            }
            if (userInfo.containsKey("github") && userInfo.get("github") != null) {
                existingUser.setGithub((String) userInfo.get("github"));
                logger.debug("更新GitHub地址为: {}", userInfo.get("github"));
            }
            if (userInfo.containsKey("deptId") && userInfo.get("deptId") != null) {
                existingUser.setDeptId((Integer) userInfo.get("deptId"));
                logger.debug("更新部门ID为: {}", userInfo.get("deptId"));
            }
            if (userInfo.containsKey("status") && userInfo.get("status") != null) {
                existingUser.setStatus((Integer) userInfo.get("status"));
                logger.debug("更新状态为: {}", userInfo.get("status"));
            }
            // 移除isMember字段处理，User实体中没有该字段
            if (userInfo.containsKey("isMember")) {
                logger.warn("尝试更新不存在的isMember字段，用户ID: {}", currentUser.getUserId());
                userInfo.remove("isMember");
            }

            UserDTO userDTO = userConverter.toDto(existingUser);
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