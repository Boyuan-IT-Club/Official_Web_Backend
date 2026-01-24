package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.RolePermission;
import club.boyuan.official.service.RolePermissionService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(RolePermissionController.class);
    private final RolePermissionService rolePermissionService;

    /**
     * 为角色分配权限
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 分配结果
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Boolean> assignPermissions(@RequestParam int roleId, @RequestParam List<Integer> permissionIds) {
        logger.info("为角色分配权限，角色ID: {}, 权限IDs: {}", roleId, permissionIds);
        boolean result = rolePermissionService.assignPermissions(roleId, permissionIds);
        logger.info("权限分配成功，角色ID: {}", roleId);
        return ResponseMessage.success(result);
    }

    /**
     * 为角色添加单个权限
     * @param roleId 角色ID
     * @param permissionId 权限ID
     * @return 分配结果
     */
    @PostMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Boolean> addPermissionToRole(@PathVariable int roleId, @PathVariable int permissionId) {
        logger.info("为角色添加单个权限，角色ID: {}, 权限ID: {}", roleId, permissionId);
        boolean result = rolePermissionService.addPermission(roleId, permissionId);
        logger.info("权限添加成功，角色ID: {}, 权限ID: {}, 结果: {}", roleId, permissionId, result);
        return ResponseMessage.success(result);
    }

    /**
     * 从角色移除权限
     * @param roleId 角色ID
     * @param permissionId 权限ID
     * @return 移除结果
     */
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Boolean> removePermissionFromRole(@PathVariable int roleId, @PathVariable int permissionId) {
        logger.info("从角色移除权限，角色ID: {}, 权限ID: {}", roleId, permissionId);
        // 使用removePermissions方法移除单个权限
        List<Integer> permissionIds = List.of(permissionId);
        boolean result = rolePermissionService.removePermissions(roleId, permissionIds);
        logger.info("权限移除成功，角色ID: {}, 权限ID: {}", roleId, permissionId);
        return ResponseMessage.success(result);
    }

    /**
     * 获取角色的权限ID列表
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<Integer>> getPermissionsByRoleId(@PathVariable int roleId) {
        logger.info("获取角色权限ID列表，角色ID: {}", roleId);
        List<Integer> permissionIds = rolePermissionService.getPermissionIdsByRoleId(roleId);
        return ResponseMessage.success(permissionIds);
    }

    /**
     * 获取拥有指定权限的角色列表
     * @param permissionId 权限ID
     * @return 角色列表
     */
    @GetMapping("/permission/{permissionId}/roles")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<List<Role>> getRolesByPermissionId(@PathVariable int permissionId) {
        logger.info("获取拥有指定权限的角色列表，权限ID: {}", permissionId);
        List<Role> roles = rolePermissionService.getRolesByPermissionId(permissionId);
        return ResponseMessage.success(roles);
    }

    /**
     * 批量分配权限
     * @param roleIds 角色ID列表
     * @param permissionIds 权限ID列表
     * @return 分配结果
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Boolean> batchAssignPermissions(@RequestParam List<Integer> roleIds, @RequestParam List<Integer> permissionIds) {
        logger.info("批量分配权限给多个角色，角色IDs: {}, 权限IDs: {}", roleIds, permissionIds);
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
        logger.info("批量权限分配成功");
        return ResponseMessage.success(result);
    }
}