package club.boyuan.official.controller;

import club.boyuan.official.dto.PageResultDTO;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.util.HashMap;
import java.util.List;
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
            logger.warn("权限验证失败：未提供token");
            throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED, "未提供访问令牌");
        }

        try {
            // 验证令牌并获取用户ID
            Integer userId = jwtTokenUtil.extractUserId(token);
            if (userId == null) {
                logger.warn("权限验证失败：无法从token中提取用户ID");
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED, "无法从令牌中提取用户信息");
            }

            // 检查用户是否为管理员
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.warn("权限验证失败：找不到用户ID为{}的用户", userId);
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED, "用户不存在");
            }
            
            if (!User.ROLE_ADMIN.equals(user.getRole())) {
                logger.warn("权限验证失败：用户ID为{}的用户角色为{}，不是管理员", userId, user.getRole());
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED, "需要管理员权限才能执行此操作");
            }
            
            logger.debug("权限验证成功：用户ID为{}的管理员用户{}", userId, user.getUsername());
        } catch (BusinessException e) {
            // 如果已经是BusinessException，直接重新抛出
            logger.warn("权限验证失败：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("权限验证过程中发生系统错误", e);
            throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED, "权限验证过程中发生错误：" + e.getMessage());
        }
    }

    /**
     * 管理员添加账号接口
     * @param userDTO 用户信息
     * @return 添加结果
     */
    @PostMapping("/users")
    public ResponseEntity<ResponseMessage<?>> addUser(@RequestBody UserDTO userDTO) {
        try {
            checkAdminRole();
            
            // 检查用户名是否已存在
            User existingUser = userService.getUserByUsername(userDTO.getUsername());
            if (existingUser != null) {
                logger.warn("用户名 {} 已存在", userDTO.getUsername());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ResponseMessage.error(409, "用户名已存在"));
            }

            // 保存新用户
            User newUser = userService.add(userDTO);
            logger.info("用户 {} 成功添加", newUser.getUsername());

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("userId", newUser.getUserId());
            data.put("username", newUser.getUsername());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ResponseMessage.success(data));
        } catch (BusinessException e) {
            logger.warn("添加用户失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("添加用户时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
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

            PageResultDTO<User> userPage = userService.getUsersByConditions(role, dept, status, pageable, currentUser);

            return ResponseEntity.ok(ResponseMessage.success(userPage));
        } catch (BusinessException e) {
            logger.warn("获取用户列表失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取用户列表时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 用户状态管理接口
     * @param userId 用户ID
     * @param statusRequest 状态请求
     * @return 更新结果
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<ResponseMessage<?>> updateUserStatus(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> statusRequest) {
        try {
            String status = statusRequest.get("status");
            if (status == null || (!"active".equals(status) && !"frozen".equals(status))) {
                logger.warn("无效的状态值: {}", status);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "无效的状态值"));
            }

            User updatedUser = userService.updateUserStatus(userId, status);
            logger.info("用户 {} 的状态成功更新为 {}", userId, status);
            
            return ResponseEntity.ok(ResponseMessage.success(updatedUser));
        } catch (IllegalArgumentException e) {
            logger.warn("更新用户状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ResponseMessage.error(404, e.getMessage()));
        } catch (BusinessException e) {
            logger.warn("更新用户状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新用户状态时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 冻结/解冻用户接口
     * @param userId 用户ID
     * @param statusRequest 状态请求 (frozen 或 active)
     * @return 更新结果
     */
    @PutMapping("/users/{userId}/freeze")
    public ResponseEntity<ResponseMessage<?>> freezeUser(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> statusRequest) {
        try {
            logger.info("开始处理用户冻结/解冻请求，目标用户ID: {}", userId);
            
            // 验证管理员权限
            checkAdminRole();
            
            String status = statusRequest.get("status");
            if (status == null || (!"frozen".equals(status) && !"active".equals(status))) {
                logger.warn("无效的冻结状态值: {}", status);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "状态值必须为 'frozen' 或 'active'"));
            }

            User updatedUser = userService.updateUserStatus(userId, status);
            String action = "frozen".equals(status) ? "冻结" : "解冻";
            logger.info("管理员成功{}用户，用户ID: {}", action, userId);
            
            return ResponseEntity.ok(ResponseMessage.success(updatedUser));
        } catch (BusinessException e) {
            logger.warn("冻结/解冻用户失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("冻结/解冻用户时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 修改用户会员状态接口（录取/取消录取）
     * @param userId 用户ID
     * @param membershipRequest 会员状态请求 (true 或 false)
     * @return 更新结果
     */
    @PutMapping("/users/{userId}/membership")
    public ResponseEntity<ResponseMessage<?>> updateUserMembership(
            @PathVariable Integer userId,
            @RequestBody Map<String, Boolean> membershipRequest) {
        try {
            logger.info("开始处理用户会员状态更新请求，目标用户ID: {}", userId);
            
            // 验证管理员权限
            checkAdminRole();
            
            Boolean isMember = membershipRequest.get("isMember");
            if (isMember == null) {
                logger.warn("缺少必要的 isMember 字段");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "缺少必要的 isMember 字段"));
            }

            User updatedUser = userService.updateUserMembership(userId, isMember);
            String action = isMember ? "录取" : "取消录取";
            logger.info("管理员成功{}用户，用户ID: {}", action, userId);
            
            return ResponseEntity.ok(ResponseMessage.success(updatedUser));
        } catch (BusinessException e) {
            logger.warn("更新用户会员状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新用户会员状态时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 批量冻结/解冻用户接口
     * @param statusRequest 状态请求 (frozen 或 active)
     * @return 更新结果
     */
    @PutMapping("/users/batch/status")
    public ResponseEntity<ResponseMessage<?>> batchUpdateUserStatus(
            @RequestBody Map<String, Object> statusRequest) {
        try {
            logger.info("开始处理批量用户状态更新请求");
            
            // 验证管理员权限
            checkAdminRole();
            
            String status = (String) statusRequest.get("status");
            List<Integer> userIds = (List<Integer>) statusRequest.get("userIds");
            
            if (status == null || (!"frozen".equals(status) && !"active".equals(status))) {
                logger.warn("无效的冻结状态值: {}", status);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "状态值必须为 'frozen' 或 'active'"));
            }
            
            if (userIds == null || userIds.isEmpty()) {
                logger.warn("用户ID列表不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "用户ID列表不能为空"));
            }

            int updatedCount = userService.batchUpdateUserStatus(userIds, status);
            String action = "frozen".equals(status) ? "冻结" : "解冻";
            logger.info("管理员成功{}{}个用户", action, updatedCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updatedCount", updatedCount);
            result.put("status", status);
            
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (BusinessException e) {
            logger.warn("批量更新用户状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("批量更新用户状态时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 批量修改用户部门接口
     * @param deptRequest 部门信息请求
     * @return 更新结果
     */
    @PutMapping("/users/batch/dept")
    public ResponseEntity<ResponseMessage<?>> batchUpdateUserDept(
            @RequestBody Map<String, Object> deptRequest) {
        try {
            logger.info("开始处理批量用户部门更新请求");
            
            // 验证管理员权限
            checkAdminRole();
            
            String dept = (String) deptRequest.get("dept");
            List<Integer> userIds = (List<Integer>) deptRequest.get("userIds");
            
            if (dept == null || dept.trim().isEmpty()) {
                logger.warn("部门信息不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "部门信息不能为空"));
            }
            
            if (userIds == null || userIds.isEmpty()) {
                logger.warn("用户ID列表不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "用户ID列表不能为空"));
            }

            int updatedCount = userService.batchUpdateUserDept(userIds, dept);
            logger.info("管理员成功更新{}个用户的部门为{}", updatedCount, dept);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updatedCount", updatedCount);
            result.put("dept", dept);
            
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (BusinessException e) {
            logger.warn("批量更新用户部门失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("批量更新用户部门时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 批量修改用户会员状态接口（录取/取消录取）
     * @param membershipRequest 会员状态请求
     * @return 更新结果
     */
    @PutMapping("/users/batch/membership")
    public ResponseEntity<ResponseMessage<?>> batchUpdateUserMembership(
            @RequestBody Map<String, Object> membershipRequest) {
        try {
            logger.info("开始处理批量用户会员状态更新请求");
            
            // 验证管理员权限
            checkAdminRole();
            
            Boolean isMember = (Boolean) membershipRequest.get("isMember");
            List<Integer> userIds = (List<Integer>) membershipRequest.get("userIds");
            
            if (isMember == null) {
                logger.warn("缺少必要的 isMember 字段");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "缺少必要的 isMember 字段"));
            }
            
            if (userIds == null || userIds.isEmpty()) {
                logger.warn("用户ID列表不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "用户ID列表不能为空"));
            }

            int updatedCount = userService.batchUpdateUserMembership(userIds, isMember);
            String action = isMember ? "录取" : "取消录取";
            logger.info("管理员成功{}{}个用户", action, updatedCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updatedCount", updatedCount);
            result.put("isMember", isMember);
            
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (BusinessException e) {
            logger.warn("批量更新用户会员状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("批量更新用户会员状态时发生服务器内部错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
}