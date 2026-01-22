package club.boyuan.official.controller;

import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.service.UserRoleService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final UserRoleService userRoleService;

    /**
     * 为用户分配角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 分配的角色列表
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<List<Role>> assignRoles(@RequestParam int userId, @RequestParam List<Integer> roleIds) {
        try {
            List<Role> roles = userRoleService.assignRoles(userId, roleIds);
            return ResponseEntity.ok(roles);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 为用户添加单个角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 用户角色关系
     */
    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Void> addRoleToUser(@PathVariable int userId, @PathVariable int roleId) {
        try {
            userRoleService.addRoleToUser(userId, roleId);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 从用户移除角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 成功响应
     */
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Void> removeRoleFromUser(@PathVariable int userId, @PathVariable int roleId) {
        try {
            boolean removed = userRoleService.removeRoleFromUser(userId, roleId);
            return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    @GetMapping("/{userId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Role>> getRolesByUserId(@PathVariable int userId) {
        try {
            List<Role> roles = userRoleService.getRolesByUserId(userId);
            return ResponseEntity.ok(roles);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取当前用户的角色列表
     * @return 当前用户的角色列表
     */
    @GetMapping("/me/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Role>> getCurrentUserRoles() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) authentication.getPrincipal();
            List<Role> roles = userRoleService.getRolesByUserId(currentUser.getUserId());
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
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
    public ResponseEntity<List<User>> getUsersByRoleId(@PathVariable int roleId, 
                                                       @RequestParam(required = false, defaultValue = "0") int page,
                                                       @RequestParam(required = false, defaultValue = "10") int size) {
        try {
            List<User> users = userRoleService.getUsersByRoleId(roleId, page, size);
            return ResponseEntity.ok(users);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 批量分配角色给多个用户
     * @param userIds 用户ID列表
     * @param roleIds 角色ID列表
     * @return 成功分配的用户数量
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Integer> batchAssignRoles(@RequestParam List<Integer> userIds, @RequestParam List<Integer> roleIds) {
        try {
            int count = userRoleService.batchAssignRoles(userIds, roleIds);
            return ResponseEntity.ok(count);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}