package club.boyuan.official.controller;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IAwardExperienceService;
import club.boyuan.official.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
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
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private IAwardExperienceService awardExperienceService;

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
     * 更新用户信息
     */
    @PutMapping("/me")
    public ResponseEntity<ResponseMessage> updateCurrentUser(@RequestBody Map<String, String> userInfo) {
        System.out.println("请求体: " + userInfo.toString());
        // 从认证信息中获取用户ID（实际项目中实现）
        Integer userId = getAuthenticatedUserId();
        User user = userService.getUserById(userId);

        if (userInfo.containsKey("password")) {
            user.setName(userInfo.get("password"));
        }
        if (userInfo.containsKey("name")) {
            user.setName(userInfo.get("name"));
        }
        if (userInfo.containsKey("phone")) {
            user.setPhone(userInfo.get("phone"));
        }
        if (userInfo.containsKey("dept")) {
            user.setDept(userInfo.get("dept"));
            System.out.println("更新后的部门: " + user.getDept());
        }

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        System.out.println("userDTO: " + userDTO.toString());
        User updatedUser = userService.edit(userDTO);

        return ResponseEntity.ok(new ResponseMessage(200, "用户信息更新成功", updatedUser));
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

    //修改
    @PutMapping
    public ResponseEntity<ResponseMessage<User>> edit(@Validated @RequestBody UserDTO user) {
        // 从请求属性中获取JWT解析的用户名
        String username = (String) request.getAttribute("username");
        // 设置用户名到UserDTO
        user.setUsername(username);
        System.out.println(user.toString());
        User userNew = userService.edit(user);
        return ResponseEntity.ok(ResponseMessage.success(userNew));
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