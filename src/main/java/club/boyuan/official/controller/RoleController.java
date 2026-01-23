package club.boyuan.official.controller;

import club.boyuan.official.dto.RoleDTO;
import club.boyuan.official.dto.PermissionDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.service.RoleService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);
    private final RoleService roleService;

    /**
     * 创建角色
     * @param roleDTO 角色DTO对象
     * @return 创建成功的角色DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<RoleDTO> createRole(@Validated @RequestBody RoleDTO roleDTO) {
        logger.info("创建角色: {}", roleDTO.getName());
        RoleDTO createdRole = roleService.createRole(roleDTO);
        logger.info("角色创建成功: {}", createdRole.getRoleId());
        return ResponseMessage.success(createdRole);
    }

    /**
     * 更新角色
     * @param roleId 角色ID
     * @param roleDTO 角色DTO对象
     * @return 更新后的角色DTO
     */
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<RoleDTO> updateRole(@PathVariable int roleId, @Validated @RequestBody RoleDTO roleDTO) {
        logger.info("更新角色: {}", roleId);
        roleDTO.setRoleId(roleId);
        RoleDTO updatedRole = roleService.updateRole(roleDTO);
        logger.info("角色更新成功: {}", roleId);
        return ResponseMessage.success(updatedRole);
    }

    /**
     * 删除角色
     * @param roleId 角色ID
     * @return 成功响应
     */
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseMessage<Void> deleteRole(@PathVariable int roleId) {
        logger.info("删除角色: {}", roleId);
        boolean deleted = roleService.deleteRole(roleId);
        if (deleted) {
            logger.info("角色删除成功: {}", roleId);
            return ResponseMessage.success();
        } else {
            logger.warn("角色删除失败，角色不存在: {}", roleId);
            return ResponseMessage.error(404, "角色不存在");
        }
    }

    /**
     * 获取角色详情
     * @param roleId 角色ID
     * @return 角色详情DTO
     */
    @GetMapping("/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<RoleDTO> getRoleById(@PathVariable int roleId) {
        logger.info("获取角色详情: {}", roleId);
        RoleDTO roleDTO = roleService.getRoleById(roleId);
        return ResponseMessage.success(roleDTO);
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
    public ResponseMessage<List<RoleDTO>> getRoles(
            @RequestParam(required = false, defaultValue = "0") int status,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "roleId,asc") String sort) {
        logger.info("获取角色列表，状态: {}, 关键词: {}, 页码: {}, 每页大小: {}, 排序: {}", status, keyword, page, size, sort);
        List<RoleDTO> roles = roleService.getRoles(status, keyword, page, size, sort);
        return ResponseMessage.success(roles);
    }

    /**
     * 获取所有启用的角色
     * @return 启用的角色DTO列表
     */
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<RoleDTO>> getAllAvailableRoles() {
        logger.info("获取所有启用的角色");
        List<RoleDTO> roles = roleService.getAllAvailableRoles();
        return ResponseMessage.success(roles);
    }

    /**
     * 获取角色的权限列表
     * @param roleId 角色ID
     * @return 权限DTO列表
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<PermissionDTO>> getPermissionsByRoleId(@PathVariable int roleId) {
        logger.info("获取角色权限列表: {}", roleId);
        List<PermissionDTO> permissions = roleService.getPermissionsByRoleId(roleId);
        return ResponseMessage.success(permissions);
    }
}