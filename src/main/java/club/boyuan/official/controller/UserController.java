package club.boyuan.official.controller;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.entity.User;
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

import java.util.List;
import java.util.stream.Collectors;

@RestController //接口方法返回对象转换成Json文本
@RequestMapping("api/user")
@AllArgsConstructor
public class UserController {

    private final IUserService userService;

    private final HttpServletRequest request;

    private final JwtTokenUtil jwtTokenUtil;

    private final IAwardExperienceService awardExperienceService;

    /**
     * 获取当前用户信息，返回响应实体
     */
    @GetMapping("/me")
    public ResponseEntity<ResponseMessage> getCurrentUser() {
        // 在实际项目中，应该从token中解析用户ID
        // 从认证信息中获取用户ID（实际项目中实现）
        Integer userId = getAuthenticatedUserId();
        User user = userService.getUserById(userId);
        List<AwardExperience> awardExperiences = awardExperienceService.getByUserId(userId);
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("user", user);
        responseData.put("awardExperiences", awardExperiences);
        return ResponseEntity.ok(new ResponseMessage(200, "success", responseData));
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未提供认证令牌");
        }

        try {
            // 验证令牌并获取用户名
            String username = jwtTokenUtil.extractUsername(token);
            if (!jwtTokenUtil.validateToken(token, username)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "令牌无效或已过期");
            }

            // 获取用户信息
            User user = userService.getUserByUsername(username);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
            }

            return user.getUserId();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "认证失败: " + e.getMessage());
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
        System.out.println("请求体: " + user.toString());
        User userNew = userService.add(user);
        return ResponseEntity.ok(ResponseMessage.success(userNew));
    }
    //查询

    @GetMapping("/{userId}")
    public ResponseEntity<ResponseMessage<?>> get(@PathVariable Integer userId) {
        User currentUser = getCurrentUserEntity();
        // 管理员可以查看所有用户，普通用户只能查看自己
        if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(userId)) {
            throw new IllegalArgumentException("权限不足，无法查看其他用户信息");
        }
        User userNew = userService.getUserById(userId);
            List<AwardExperience> awardExperiences = awardExperienceService.getByUserId(userId);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", userNew);
            responseData.put("awardExperiences", awardExperiences);
            System.out.println("已经插入award"+awardExperiences.toString());
            return ResponseEntity.ok(new ResponseMessage(200, "success", responseData));
    }

    //删除
    @DeleteMapping("/{userId}")
    public ResponseEntity<ResponseMessage<Void>> delete(@PathVariable Integer userId) {
        userService.deleteUserById(userId);
        return ResponseEntity.ok(ResponseMessage.success());
    }

    /**
     * 更新指定用户信息（需传入ID，支持部分字段更新）
     */
    @PutMapping
    public ResponseEntity<ResponseMessage> edit(@RequestBody Map<String, Object> userInfo) {
        User currentUser = getCurrentUserEntity();
        
        // 从请求体获取userId
        Integer targetUserId = (Integer) userInfo.get("userId");
        if (targetUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体中必须包含userId");
        }

        // 管理员可以更新所有用户，普通用户只能更新自己
        if (!User.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getUserId().equals(targetUserId)) {
            throw new IllegalArgumentException("权限不足，无法更新其他用户信息");
        }

        User existingUser = userService.getUserById(targetUserId);
        // 移除userId，避免更新用户ID
        userInfo.remove("userId");
        if (existingUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
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
                throw new IllegalArgumentException("权限不足，无法修改用户角色");
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
    }

    @GetMapping("/all")
    public ResponseEntity<ResponseMessage<List<User>>> getAllUsers() {
        User currentUser = getCurrentUserEntity();
        checkAdminPermission(currentUser);
        List<User> users = userService.getAllUsers(currentUser);
        // 只保留用户基础信息
        List<User> basicUsers = users.stream().map(user -> {
            User basicUser = new User();
            basicUser.setUserId(user.getUserId());
            basicUser.setUsername(user.getUsername());
            basicUser.setName(user.getName());
            basicUser.setPhone(user.getPhone());
            basicUser.setDept(user.getDept());
            basicUser.setRole(user.getRole());
            return basicUser;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ResponseMessage.success(basicUsers));
    }

}