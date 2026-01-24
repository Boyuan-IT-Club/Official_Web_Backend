package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.UserRoleService;
import club.boyuan.official.utils.JwtTokenUtil;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户角色管理Controller
 *
 * @author zewan
 * @version 1.0
 * @date 2026-01-22 22:30
 * @since 2026
 */
@RestController
@RequestMapping("/api/user-roles")
@AllArgsConstructor
public class UserRoleController {

    private static final Logger logger = LoggerFactory.getLogger(UserRoleController.class);
    private final UserRoleService userRoleService;
    private final IUserService userService;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 为用户分配角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 分配的角色列表
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<List<Role>> assignRoles(@RequestParam int userId, @RequestParam List<Integer> roleIds) {
        logger.info("为用户分配角色，用户ID: {}, 角色IDs: {}", userId, roleIds);
        List<Role> roles = userRoleService.assignRoles(userId, roleIds);
        logger.info("角色分配成功，用户ID: {}", userId);
        return ResponseMessage.success(roles);
    }

    /**
     * 为用户添加单个角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 更新后的角色列表
     */
    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<List<Role>> addRoleToUser(@PathVariable int userId, @PathVariable int roleId) {
        logger.info("为用户添加单个角色，用户ID: {}, 角色ID: {}", userId, roleId);
        userRoleService.addRoleToUser(userId, roleId);
        logger.info("角色添加成功，用户ID: {}, 角色ID: {}", userId, roleId);
        List<Role> roles = userRoleService.getRolesByUserId(userId);
        return ResponseMessage.success(roles);
    }

    /**
     * 从用户移除角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 更新后的角色列表
     */
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<List<Role>> removeRoleFromUser(@PathVariable int userId, @PathVariable int roleId) {
        logger.info("从用户移除角色，用户ID: {}, 角色ID: {}", userId, roleId);
        boolean removed = userRoleService.removeRoleFromUser(userId, roleId);
        if (removed) {
            logger.info("角色移除成功，用户ID: {}, 角色ID: {}", userId, roleId);
            List<Role> roles = userRoleService.getRolesByUserId(userId);
            return ResponseMessage.success(roles);
        } else {
            logger.warn("角色移除失败，用户ID: {}, 角色ID: {}", userId, roleId);
            return ResponseMessage.error(404, "用户角色关系不存在");
        }
    }

    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    @GetMapping("/{userId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<Role>> getRolesByUserId(@PathVariable int userId) {
        logger.info("获取用户角色列表，用户ID: {}", userId);
        List<Role> roles = userRoleService.getRolesByUserId(userId);
        return ResponseMessage.success(roles);
    }

    /**
     * 获取当前用户的角色列表
     * @return 当前用户的角色列表
     */
    @GetMapping("/me/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<Role>> getCurrentUserRoles(HttpServletRequest request) {
        logger.info("获取当前用户角色列表");
        
        // 从请求头获取Authorization令牌
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            logger.warn("未找到有效的Authorization头");
            return ResponseMessage.error(401, "未授权");
        }
        
        // 提取令牌
        String token = authorizationHeader.substring(7);
        
        // 使用JwtTokenUtil提取用户名
        try {
            String username = jwtTokenUtil.extractUsername(token);
            if (username == null) {
                logger.warn("无法从令牌中提取用户名");
                return ResponseMessage.error(401, "未授权");
            }
            
            // 查询用户信息
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                logger.warn("用户不存在: {}", username);
                return ResponseMessage.error(404, "用户不存在");
            }
            
            // 获取用户角色列表
            List<Role> roles = userRoleService.getRolesByUserId(currentUser.getUserId());
            return ResponseMessage.success(roles);
        } catch (Exception e) {
            logger.error("获取当前用户角色列表失败", e);
            return ResponseMessage.error(401, "未授权");
        }
    }

    /**
     * 获取拥有指定角色的用户列表
     * @param roleId 角色ID
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表
     */
    @GetMapping("/role/{roleId}/users")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<List<User>> getUsersByRoleId(@PathVariable int roleId, 
                                                       @RequestParam(required = false, defaultValue = "0") int page,
                                                       @RequestParam(required = false, defaultValue = "10") int size) {
        logger.info("获取拥有指定角色的用户列表，角色ID: {}, 页码: {}, 每页大小: {}", roleId, page, size);
        List<User> users = userRoleService.getUsersByRoleId(roleId, page, size);
        return ResponseMessage.success(users);
    }

    /**
     * 批量分配角色给多个用户
     * @param userIds 用户ID列表
     * @param roleIds 角色ID列表
     * @return 成功分配的用户数量
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Integer> batchAssignRoles(@RequestParam List<Integer> userIds, @RequestParam List<Integer> roleIds) {
        logger.info("批量分配角色给多个用户，用户IDs: {}, 角色IDs: {}", userIds, roleIds);
        int count = userRoleService.batchAssignRoles(userIds, roleIds);
        logger.info("批量角色分配成功，分配用户数量: {}", count);
        return ResponseMessage.success(count);
    }
}