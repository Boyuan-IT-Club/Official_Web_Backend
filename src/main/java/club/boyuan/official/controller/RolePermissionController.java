package club.boyuan.official.controller;

import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.RolePermission;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.service.RolePermissionService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;

/**
 * 角色权限管理Controller
 *
 * @author zewan
 * @version 1.0
 * @date 2026-01-22 22:40
 * @since 2026
 */
@RestController
@RequestMapping("/api/role-permissions")
@AllArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    /**
     * 为角色分配权限
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 分配结果
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Boolean> assignPermissions(@RequestParam int roleId, @RequestParam List<Integer> permissionIds) {
        try {
            boolean result = rolePermissionService.assignPermissions(roleId, permissionIds);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 为角色添加单个权限
     * @param roleId 角色ID
     * @param permissionId 权限ID
     * @return 分配结果
     */
    @PostMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Boolean> addPermissionToRole(@PathVariable int roleId, @PathVariable int permissionId) {
        try {
            // 使用assignPermissions方法添加单个权限
            List<Integer> permissionIds = List.of(permissionId);
            boolean result = rolePermissionService.assignPermissions(roleId, permissionIds);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 从角色移除权限
     * @param roleId 角色ID
     * @param permissionId 权限ID
     * @return 移除结果
     */
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Boolean> removePermissionFromRole(@PathVariable int roleId, @PathVariable int permissionId) {
        try {
            // 使用removePermissions方法移除单个权限
            List<Integer> permissionIds = List.of(permissionId);
            boolean result = rolePermissionService.removePermissions(roleId, permissionIds);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取角色的权限ID列表
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Integer>> getPermissionsByRoleId(@PathVariable int roleId) {
        try {
            List<Integer> permissionIds = rolePermissionService.getPermissionIdsByRoleId(roleId);
            return ResponseEntity.ok(permissionIds);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取拥有指定权限的角色列表
     * @param permissionId 权限ID
     * @return 角色列表
     */
    @GetMapping("/permission/{permissionId}/roles")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<List<Role>> getRolesByPermissionId(@PathVariable int permissionId) {
        try {
            List<Role> roles = rolePermissionService.getRolesByPermissionId(permissionId);
            return ResponseEntity.ok(roles);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 批量分配权限
     * @param roleIds 角色ID列表
     * @param permissionIds 权限ID列表
     * @return 分配结果
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Boolean> batchAssignPermissions(@RequestParam List<Integer> roleIds, @RequestParam List<Integer> permissionIds) {
        try {
            // 构建RolePermission对象列表
            List<RolePermission> rolePermissions = new ArrayList<>();
            for (Integer roleId : roleIds) {
                for (Integer permissionId : permissionIds) {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setRoleId(roleId);
                    rolePermission.setPermissionId(permissionId);
                    rolePermissions.add(rolePermission);
                }
            }
            boolean result = rolePermissionService.batchAssignPermissions(rolePermissions);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}