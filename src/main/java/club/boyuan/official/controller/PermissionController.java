package club.boyuan.official.controller;

import club.boyuan.official.dto.PermissionDTO;
import club.boyuan.official.entity.Permission;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.service.PermissionService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理Controller
 *
 * @author zewan
 * @version 1.0
 * @date 2026-01-22 22:20
 * @since 2026
 */
@RestController
@RequestMapping("/api/permissions")
@AllArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 创建权限
     * @param permissionDTO 权限DTO对象
     * @return 创建成功的权限DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<PermissionDTO> createPermission(@Validated @RequestBody PermissionDTO permissionDTO) {
        try {
            PermissionDTO createdPermission = permissionService.createPermission(permissionDTO);
            return new ResponseEntity<>(createdPermission, HttpStatus.CREATED);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 更新权限
     * @param permissionId 权限ID
     * @param permissionDTO 权限DTO对象
     * @return 更新后的权限DTO
     */
    @PutMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<PermissionDTO> updatePermission(@PathVariable int permissionId, @Validated @RequestBody PermissionDTO permissionDTO) {
        try {
            permissionDTO.setPermissionId(permissionId);
            PermissionDTO updatedPermission = permissionService.updatePermission(permissionDTO);
            return ResponseEntity.ok(updatedPermission);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 删除权限
     * @param permissionId 权限ID
     * @return 成功响应
     */
    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Void> deletePermission(@PathVariable int permissionId) {
        try {
            boolean deleted = permissionService.deletePermission(permissionId);
            return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取权限详情
     * @param permissionId 权限ID
     * @return 权限详情DTO
     */
    @GetMapping("/{permissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PermissionDTO> getPermissionById(@PathVariable int permissionId) {
        try {
            PermissionDTO permissionDTO = permissionService.getPermissionById(permissionId);
            return ResponseEntity.ok(permissionDTO);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 分页获取权限列表
     * @param resourceIdentifier 资源标识符
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @param sort 排序
     * @return 权限DTO列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PermissionDTO>> getPermissions(
            @RequestParam(required = false, defaultValue = "") String resourceIdentifier,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "permissionId,asc") String sort) {
        try {
            List<PermissionDTO> permissions = permissionService.getPermissions(resourceIdentifier, keyword, page, size, sort);
            return ResponseEntity.ok(permissions);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取所有权限（不分页）
     * @return 所有权限DTO列表
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PermissionDTO>> getAllPermissions() {
        try {
            List<PermissionDTO> permissions = permissionService.getAllPermissions();
            return ResponseEntity.ok(permissions);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}