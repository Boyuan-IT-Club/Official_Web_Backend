package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.UserRoleService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
     * @return 用户角色关系
     */
    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Void> addRoleToUser(@PathVariable int userId, @PathVariable int roleId) {
        logger.info("为用户添加单个角色，用户ID: {}, 角色ID: {}", userId, roleId);
        userRoleService.addRoleToUser(userId, roleId);
        logger.info("角色添加成功，用户ID: {}, 角色ID: {}", userId, roleId);
        return ResponseMessage.success();
    }

    /**
     * 从用户移除角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 成功响应
     */
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Void> removeRoleFromUser(@PathVariable int userId, @PathVariable int roleId) {
        logger.info("从用户移除角色，用户ID: {}, 角色ID: {}", userId, roleId);
        boolean removed = userRoleService.removeRoleFromUser(userId, roleId);
        if (removed) {
            logger.info("角色移除成功，用户ID: {}, 角色ID: {}", userId, roleId);
            return ResponseMessage.success();
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
    public ResponseMessage<List<Role>> getCurrentUserRoles() {
        logger.info("获取当前用户角色列表");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        List<Role> roles = userRoleService.getRolesByUserId(currentUser.getUserId());
        return ResponseMessage.success(roles);
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