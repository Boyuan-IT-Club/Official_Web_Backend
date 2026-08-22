package club.boyuan.official.domain.user.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.user.dto.AdminUserPageDTO;
import club.boyuan.official.domain.user.dto.UserDTO;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.common.utils.RedisUtil;
import club.boyuan.official.common.utils.SecurityUtil;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端控制器。
 * <p>
 * 鉴权统一由 {@link PreAuthorize} 保证，认证由 JwtAuthenticationFilter 完成，
 * 业务异常统一交给 GlobalExceptionHandler，Controller 只保留业务结果分支（如 201/409）。
 */
@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
public class AdminController {

    private final IUserService userService;

    private final CosStorageService cosStorageService;

    private final club.boyuan.official.domain.user.service.UserRoleService userRoleService;

    private final RedisUtil redisUtil;

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    /**
     * 管理员添加账号接口
     */
    @PostMapping("/users")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> addUser(@RequestBody UserDTO userDTO) {
        User existingUser = userService.getUserByUsername(userDTO.getUsername());
        if (existingUser != null) {
            logger.warn("用户名 {} 已存在", userDTO.getUsername());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ResponseMessage.error(409, "用户名已存在"));
        }

        User newUser = userService.add(userDTO);
        logger.info("用户 {} 成功添加", newUser.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", newUser.getUserId());
        data.put("username", newUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseMessage.success(data));
    }

    /**
     * 为用户赋予管理员权限接口
     */
    @PostMapping("/users/{userId}/grant-admin")
    @PreAuthorize("hasAnyAuthority('admin:grant', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> grantAdminPermission(@PathVariable Integer userId) {
        String adminUsername = SecurityUtil.getCurrentUsername();
        User adminUser = userService.getUserByUsername(adminUsername);

        User targetUser = userService.getUserById(userId);
        if (targetUser == null) {
            logger.warn("管理员 {} 尝试为不存在的用户ID {} 授权", adminUsername, userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ResponseMessage.error(BusinessExceptionEnum.USER_NOT_FOUND.getCode(),
                            BusinessExceptionEnum.USER_NOT_FOUND.getMessage()));
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(userId);
        userService.edit(userDTO);

        logger.info("管理员 {} 成功为用户 {} 授予管理员权限", adminUsername, targetUser.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUser.getUserId());
        data.put("username", targetUser.getUsername());
        data.put("grantedBy", adminUser.getUsername());
        data.put("message", "成功授予管理员权限");
        return ResponseEntity.ok(new ResponseMessage<>(200, "成功授予管理员权限", data));
    }

    /**
     * 获取用户列表接口（分页 + 条件查询）
     */
    // 只读放宽:admin:manage(超管,可读可写)或 user:view(管理员,只读)。
    // 本控制器其余接口一律保持 admin:manage —— 只读角色能看不能改的边界就在这里,
    // 新增写接口时不要顺手抄这一行。
    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('admin:manage', 'user:view')")
    public ResponseEntity<ResponseMessage<?>> getUsers(
            @RequestParam(required = false) String roleGroup,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "userId", direction = Sort.Direction.ASC) Pageable pageable) {
        User currentUser = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        PageResultDTO<User> userPage = userService.getUsersByConditions(roleGroup, role, dept, status, keyword, pageable, currentUser);
        // 填充 RBAC 角色（user 表的 role 列为注册期遗留字段，展示与分配统一走 user_role 关联）
        if (userPage != null && userPage.getContent() != null) {
            userPage.getContent().forEach(u -> {
                u.setAvatar(cosStorageService.resolveAvatarUrl(u.getAvatar()));
                try {
                    u.setRoles(userRoleService.getRolesByUserId(u.getUserId()));
                } catch (Exception e) {
                    logger.warn("填充用户{}的角色失败: {}", u.getUserId(), e.getMessage());
                }
            });
        }
        // 统计卡的三个计数走全库,与当前分页/筛选无关;键名兼容上游 DTO,
        // 语义统一为 RBAC 分组(见 ADR-0001)。前端统计卡已迁移到 GET /users/stats。
        Map<String, Object> stats = userService.getUserStats();
        AdminUserPageDTO body = new AdminUserPageDTO(
                userPage,
                ((Number) stats.getOrDefault("memberCount", 0)).longValue(),
                ((Number) stats.getOrDefault("nonMemberCount", 0)).longValue(),
                ((Number) stats.getOrDefault("frozen", 0)).longValue());
        return ResponseEntity.ok(ResponseMessage.success(body));
    }

    /**
     * 用户分类统计（全量）：total / frozen / adminCount / memberCount / nonMemberCount。
     * 独立于分页列表，供统计卡片使用。只读，故与 getUsers 同门槛（user:view 可看统计）。
     */
    @GetMapping("/users/stats")
    @PreAuthorize("hasAnyAuthority('admin:manage', 'user:manage', 'user:view')")
    public ResponseEntity<ResponseMessage<?>> getUserStats() {
        Map<String, Object> stats = userService.getUserStats();
        return ResponseEntity.ok(ResponseMessage.success(stats));
    }

    /**
     * 用户状态管理接口
     */
    @PutMapping("/users/{userId}/status")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> updateUserStatus(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> statusRequest) {
        String status = statusRequest.get("status");
        if (status == null || (!"active".equals(status) && !"frozen".equals(status))) {
            logger.warn("无效的状态值: {}", status);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(400, "无效的状态值"));
        }
        try {
            User updatedUser = userService.updateUserStatus(userId, status);
            logger.info("用户 {} 的状态成功更新为 {}", userId, status);
            return ResponseEntity.ok(ResponseMessage.success(updatedUser));
        } catch (IllegalArgumentException e) {
            // 用户不存在等非法参数场景，保持 404 语义
            logger.warn("更新用户状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseMessage.error(404, e.getMessage()));
        }
    }

    /**
     * 冻结/解冻用户接口
     */
    @PutMapping("/users/{userId}/freeze")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> freezeUser(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> statusRequest) {
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
    }

    /**
     * 修改用户会员状态接口（录取/取消录取）
     */
    @PutMapping("/users/{userId}/membership")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> updateUserMembership(
            @PathVariable Integer userId,
            @RequestBody Map<String, Boolean> membershipRequest) {
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
    }

    /**
     * 批量冻结/解冻用户接口
     */
    @PutMapping("/users/batch-status")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> batchUpdateUserStatus(
            @RequestBody Map<String, Object> statusRequest) {
        String status = (String) statusRequest.get("status");
        @SuppressWarnings("unchecked")
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
    }

    /**
     * 批量修改用户部门接口
     */
    @PutMapping("/users/batch-dept")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> batchUpdateUserDept(
            @RequestBody Map<String, Object> deptRequest) {
        String dept = (String) deptRequest.get("dept");
        @SuppressWarnings("unchecked")
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
    }

    /**
     * 批量修改用户会员状态接口（录取/取消录取）
     */
    @PutMapping("/users/batch-membership")
    @PreAuthorize("hasAnyAuthority('user:manage', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> batchUpdateUserMembership(
            @RequestBody Map<String, Object> membershipRequest) {
        Boolean isMember = (Boolean) membershipRequest.get("isMember");
        @SuppressWarnings("unchecked")
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
    }

    /**
     * 清理Redis缓存接口
     */
    @PostMapping("/cache/clear")
    @PreAuthorize("hasAnyAuthority('system:ops', 'admin:manage')")
    public ResponseEntity<ResponseMessage<?>> clearCache(
            @RequestBody(required = false) Map<String, String> cacheRequest) {
        String cacheType = cacheRequest != null ? cacheRequest.get("type") : "field_definition";

        switch (cacheType) {
            case "field_definition":
                redisUtil.clearFieldDefinitionCache();
                logger.info("管理员清理了字段定义缓存");
                return ResponseEntity.ok(ResponseMessage.success("字段定义缓存清理成功"));

            case "all":
                redisUtil.clearAllCache();
                logger.info("管理员清理了所有缓存");
                return ResponseEntity.ok(ResponseMessage.success("所有缓存清理成功"));

            default:
                if (cacheType.contains("*")) {
                    redisUtil.clearCacheByPattern(cacheType);
                    logger.info("管理员清理了模式 '{}' 的缓存", cacheType);
                    return ResponseEntity.ok(ResponseMessage.success("模式缓存清理成功: " + cacheType));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResponseMessage.error(400, "不支持的缓存类型: " + cacheType));
        }
    }
}
