package club.boyuan.official.controller;

import club.boyuan.official.dto.RoleDTO;
import club.boyuan.official.dto.PermissionDTO;
import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.service.RoleService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理Controller
 *
 * @author zewan
 * @version 1.0
 * @date 2026-01-22 22:10
 * @since 2026
 */
@RestController
@RequestMapping("/api/roles")
@AllArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 创建角色
     * @param roleDTO 角色DTO对象
     * @return 创建成功的角色DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<RoleDTO> createRole(@Validated @RequestBody RoleDTO roleDTO) {
        try {
            RoleDTO createdRole = roleService.createRole(roleDTO);
            return new ResponseEntity<>(createdRole, HttpStatus.CREATED);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 更新角色
     * @param roleId 角色ID
     * @param roleDTO 角色DTO对象
     * @return 更新后的角色DTO
     */
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<RoleDTO> updateRole(@PathVariable int roleId, @Validated @RequestBody RoleDTO roleDTO) {
        try {
            roleDTO.setRoleId(roleId);
            RoleDTO updatedRole = roleService.updateRole(roleDTO);
            return ResponseEntity.ok(updatedRole);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 删除角色
     * @param roleId 角色ID
     * @return 成功响应
     */
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Void> deleteRole(@PathVariable int roleId) {
        try {
            boolean deleted = roleService.deleteRole(roleId);
            return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取角色详情
     * @param roleId 角色ID
     * @return 角色详情DTO
     */
    @GetMapping("/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable int roleId) {
        try {
            RoleDTO roleDTO = roleService.getRoleById(roleId);
            return ResponseEntity.ok(roleDTO);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取角色列表
     * @param status 状态
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @param sort 排序
     * @return 角色DTO列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleDTO>> getRoles(
            @RequestParam(required = false, defaultValue = "0") int status,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "roleId,asc") String sort) {
        try {
            List<RoleDTO> roles = roleService.getRoles(status, keyword, page, size, sort);
            return ResponseEntity.ok(roles);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取所有启用的角色
     * @return 启用的角色DTO列表
     */
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleDTO>> getAllAvailableRoles() {
        try {
            List<RoleDTO> roles = roleService.getAllAvailableRoles();
            return ResponseEntity.ok(roles);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取角色的权限列表
     * @param roleId 角色ID
     * @return 权限DTO列表
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PermissionDTO>> getPermissionsByRoleId(@PathVariable int roleId) {
        try {
            List<PermissionDTO> permissions = roleService.getPermissionsByRoleId(roleId);
            return ResponseEntity.ok(permissions);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}