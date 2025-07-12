package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
  import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import club.boyuan.official.utils.JwtTokenUtil;


@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private HttpServletRequest request;

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
     * 验证JWT令牌并检查管理员权限
     */
    private void checkAdminRole() {
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

            // 检查用户是否为管理员
            User user = userService.getUserByUsername(username);
            if (user == null || !User.ROLE_ADMIN.equals(user.getRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有管理员权限");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "认证失败: " + e.getMessage());
        }
    }

    /**
     * 管理员添加账号接口
     */
    @PostMapping("/users")
    public ResponseEntity<ResponseMessage> addUser(@RequestBody UserDTO userDTO) {
        checkAdminRole();
        // 检查用户名是否已存在
        User existingUser = userService.getUserByUsername(userDTO.getUsername());
        if (existingUser != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseMessage(409, "用户名已存在", null));
        }

        // 保存新用户
        User newUser = userService.add(userDTO);

        // 构建响应数据
        Map<String, Object> data = new HashMap<>();
        data.put("userId", newUser.getUserId());
        data.put("username", newUser.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseMessage(201, "注册成功", data));
    }

    /**
     * 获取用户列表接口
     */
    @GetMapping("/users")
    public ResponseEntity<ResponseMessage> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        checkAdminRole();
        
        // 获取当前登录用户（实际项目中应从安全上下文获取）
        User currentUser = new User();
        currentUser.setRole(User.ROLE_ADMIN);
        
        // 创建分页对象
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        // 输出请求
        System.out.println("Controller层");
        System.out.println("role: " + role);
        System.out.println("dept: " + dept);
        System.out.println("status: " + status);
        System.out.println("pageable: " + pageable);
        System.out.println("currentUser: " + currentUser);

        Page<User> userPage = userService.getUsersByConditions(role, dept, status, pageable, currentUser);

        Map<String, Object> data = new HashMap<>();
        data.put("total", userPage.getTotalElements());
        data.put("users", userPage.getContent());
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("totalPages", userPage.getTotalPages());
        // 输出data
        System.out.println("data: " + data);
        return ResponseEntity.ok(new ResponseMessage(200, "success", data));
    }

    /**
     * 用户状态管理接口
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<ResponseMessage> updateUserStatus(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> statusRequest) {
        String status = statusRequest.get("status");
        if (status == null || (!"active".equals(status) && !"frozen".equals(status))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage(400, "无效的状态值", null));
        }

        try {
            User updatedUser = userService.updateUserStatus(userId, status);
            return ResponseEntity.ok(new ResponseMessage(200, "状态更新成功", updatedUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage(404, e.getMessage(), null));
        }
    }
}