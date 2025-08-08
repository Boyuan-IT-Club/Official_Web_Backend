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
    public ResponseEntity<ResponseMessage> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        try {
            // 获取当前用户
            Integer userId = getAuthenticatedUserId();
            User user = userService.getUserById(userId);
            
            // 上传文件并获取路径
            String avatarPath = FileUploadUtil.uploadAvatar(file);
            
            // 更新用户头像信息
            user.setAvatar(avatarPath);
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            userService.edit(userDTO);
            
            Map<String, String> responseData = new HashMap<>();
            responseData.put("avatar", avatarPath);
            
            logger.info("用户ID为{}的用户成功上传头像，路径为{}", userId, avatarPath);
            return ResponseEntity.ok(new ResponseMessage(200, "头像上传成功", responseData));
        } catch (Exception e) {
            logger.error("头像上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage(500, "头像上传失败: " + e.getMessage(), null));
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
            return ResponseEntity.ok(new ResponseMessage(200, "success", responseData));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage(500, "服务器内部错误: " + e.getMessage(), null));
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

            // 仅更新传入的非空字段
            if (userInfo.containsKey("username")) {
                existingUser.setUsername((String) userInfo.get("username"));
            }
            if (userInfo.containsKey("password")) {
                existingUser.setPassword((String) userInfo.get("password"));
            }
            if (userInfo.containsKey("email")) {
                existingUser.setEmail((String) userInfo.get("email"));
            }
            if (userInfo.containsKey("name")) {
                existingUser.setName((String) userInfo.get("name"));
            }
            if (userInfo.containsKey("phone")) {
                existingUser.setPhone((String) userInfo.get("phone"));
            }
            if (userInfo.containsKey("dept")) {
                existingUser.setDept((String) userInfo.get("dept"));
            }
            if (userInfo.containsKey("status")) {
                existingUser.setStatus((Boolean) userInfo.get("status"));
            }
            if (userInfo.containsKey("isMember")) {
                existingUser.setIsMember((Boolean) userInfo.get("isMember"));
            }

            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(existingUser, userDTO);
            userService.edit(userDTO);

            return ResponseEntity.ok(new ResponseMessage(200, "信息已更新", null));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage(500, "服务器内部错误: " + e.getMessage(), null));
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
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
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
            
            // 从请求体获取userId
            Integer targetUserId = (Integer) userInfo.get("userId");
            if (targetUserId == null) {
                throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
            }

            // 管理员可以更新所有用户，普通用户只能更新自己
            if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(targetUserId)) {
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
            }

            User existingUser = userService.getUserById(targetUserId);
            // 移除userId，避免更新用户ID
            userInfo.remove("userId");
            if (existingUser == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }

            // 仅更新传入的非空字段
            if (userInfo.containsKey("username")) {
                existingUser.setUsername((String) userInfo.get("username"));
            }
            if (userInfo.containsKey("password")) {
                existingUser.setPassword((String) userInfo.get("password"));
            }
            if (userInfo.containsKey("email")) {
                existingUser.setEmail((String) userInfo.get("email"));
            }
            if (userInfo.containsKey("role")) {
                // 只有管理员可以修改角色
                if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
                    existingUser.setRole((String) userInfo.get("role"));
                } else {
                    throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
                }
            }
            if (userInfo.containsKey("name")) {
                existingUser.setName((String) userInfo.get("name"));
            }
            if (userInfo.containsKey("phone")) {
                existingUser.setPhone((String) userInfo.get("phone"));
            }
            if (userInfo.containsKey("dept")) {
                existingUser.setDept((String) userInfo.get("dept"));
            }
            if (userInfo.containsKey("status")) {
                existingUser.setStatus((Boolean) userInfo.get("status"));
            }
            if (userInfo.containsKey("isMember")) {
                existingUser.setIsMember((Boolean) userInfo.get("isMember"));
            }

            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(existingUser, userDTO);
            userService.edit(userDTO);

            return ResponseEntity.ok(new ResponseMessage(200, "信息已更新", null));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }
}