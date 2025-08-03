package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IUserService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import club.boyuan.official.utils.JwtTokenUtil;


@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
public class AdminController {

    private final IUserService userService;

    private final JwtTokenUtil jwtTokenUtil;

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final HttpServletRequest request;

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
            throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
        }

        try {
            // 验证令牌并获取用户ID
            Integer userId = jwtTokenUtil.extractUserId(token);
            if (userId == null) {
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }

            // 检查用户是否为管理员
            User user = userService.getUserById(userId);
            if (user == null || !User.ROLE_ADMIN.equals(user.getRole())) {
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED, e.getMessage());
        }
    }

    /**
     * 管理员添加账号接口
     */
    @PostMapping("/users")
    public ResponseEntity<ResponseMessage> addUser(@RequestBody UserDTO userDTO) {
        try {
            checkAdminRole();
            
            // 检查用户名是否已存在
            User existingUser = userService.getUserByUsername(userDTO.getUsername());
            if (existingUser != null) {
                logger.warn("用户名 {} 已存在", userDTO.getUsername());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ResponseMessage(409, "用户名已存在", null));
            }

            // 保存新用户
            User newUser = userService.add(userDTO);
            logger.info("用户 {} 成功添加", newUser.getUsername());

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("userId", newUser.getUserId());
            data.put("username", newUser.getUsername());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseMessage(201, "注册成功", data));
        } catch (BusinessException e) {
            logger.warn("添加用户失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("添加用户时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }

    /**
     * 获取用户列表接口
     * 支持分页和条件查询
     */
    @GetMapping("/users")
    public ResponseEntity<ResponseMessage<?>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "userId", direction = Sort.Direction.ASC) Pageable pageable) {
        try {
            checkAdminRole();
            
            // 从JWT令牌获取当前登录用户信息
            String token = getTokenFromHeader();
            String username = jwtTokenUtil.extractUsername(token);
            User currentUser = userService.getUserByUsername(username);

            Page<User> userPage = userService.getUsersByConditions(role, dept, status, pageable, currentUser);

            Map<String, Object> data = new HashMap<>();
            data.put("users", userPage.getContent());
            data.put("currentPage", userPage.getNumber() + 1);
            data.put("totalPages", userPage.getTotalPages());
            data.put("totalElements", userPage.getTotalElements());
            data.put("size", userPage.getSize());
            
            return ResponseEntity.ok(new ResponseMessage<>(200, "查询成功", data));
        } catch (BusinessException e) {
            logger.warn("获取用户列表失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取用户列表时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }

    /**
     * 用户状态管理接口
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<ResponseMessage> updateUserStatus(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> statusRequest) {
        try {
            String status = statusRequest.get("status");
            if (status == null || (!"active".equals(status) && !"frozen".equals(status))) {
                logger.warn("无效的状态值: {}", status);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseMessage(400, "无效的状态值", null));
            }

            User updatedUser = userService.updateUserStatus(userId, status);
            logger.info("用户 {} 的状态成功更新为 {}", userId, status);
            
            return ResponseEntity.ok(new ResponseMessage(200, "状态更新成功", updatedUser));
        } catch (IllegalArgumentException e) {
            logger.warn("更新用户状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage(404, e.getMessage(), null));
        } catch (BusinessException e) {
            logger.warn("更新用户状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("更新用户状态时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage(500, "服务器内部错误: " + e.getMessage(), null));
        }
    }
}