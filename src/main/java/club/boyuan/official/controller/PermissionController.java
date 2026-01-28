package club.boyuan.official.controller;

import club.boyuan.official.dto.PermissionDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Permission;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.service.PermissionService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(PermissionController.class);
    private final PermissionService permissionService;

    /**
     * 创建权限
     * @param permissionDTO 权限DTO对象
     * @return 创建成功的权限DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<ResponseMessage<PermissionDTO>> createPermission(@Validated @RequestBody PermissionDTO permissionDTO) {
        try {
            logger.info("创建权限: {}", permissionDTO.getPermissionName());
            PermissionDTO createdPermission = permissionService.createPermission(permissionDTO);
            logger.info("权限创建成功: {}", createdPermission.getPermissionId());
            return ResponseEntity.ok(ResponseMessage.success(createdPermission));
        } catch (BusinessException e) {
            logger.error("创建权限失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("创建权限时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
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
    public ResponseEntity<ResponseMessage<PermissionDTO>> updatePermission(@PathVariable int permissionId, @Validated @RequestBody PermissionDTO permissionDTO) {
        try {
            logger.info("更新权限: {}", permissionId);
            permissionDTO.setPermissionId(permissionId);
            PermissionDTO updatedPermission = permissionService.updatePermission(permissionDTO);
            logger.info("权限更新成功: {}", permissionId);
            return ResponseEntity.ok(ResponseMessage.success(updatedPermission));
        } catch (BusinessException e) {
            logger.error("更新权限失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("更新权限时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 删除权限
     * @param permissionId 权限ID
     * @return 成功响应
     */
    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<ResponseMessage<Void>> deletePermission(@PathVariable int permissionId) {
        try {
            logger.info("删除权限: {}", permissionId);
            boolean deleted = permissionService.deletePermission(permissionId);
            if (deleted) {
                logger.info("权限删除成功: {}", permissionId);
                return ResponseEntity.ok(ResponseMessage.success());
            } else {
                logger.warn("权限删除失败，权限不存在: {}", permissionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseMessage.error(404, "权限不存在"));
            }
        } catch (BusinessException e) {
            logger.error("删除权限失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("删除权限时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 获取权限详情
     * @param permissionId 权限ID
     * @return 权限详情DTO
     */
    @GetMapping("/{permissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<PermissionDTO>> getPermissionById(@PathVariable int permissionId) {
        try {
            logger.info("获取权限详情: {}", permissionId);
            PermissionDTO permissionDTO = permissionService.getPermissionById(permissionId);
            return ResponseEntity.ok(ResponseMessage.success(permissionDTO));
        } catch (BusinessException e) {
            logger.error("获取权限详情失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取权限详情时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
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
    public ResponseEntity<ResponseMessage<List<PermissionDTO>>> getPermissions(
            @RequestParam(required = false, defaultValue = "") String resourceIdentifier,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "permissionId,asc") String sort) {
        try {
            logger.info("获取权限列表，资源标识符: {}, 关键词: {}, 页码: {}, 每页大小: {}, 排序: {}", resourceIdentifier, keyword, page, size, sort);
            List<PermissionDTO> permissions = permissionService.getPermissions(resourceIdentifier, keyword, page, size, sort);
            return ResponseEntity.ok(ResponseMessage.success(permissions));
        } catch (BusinessException e) {
            logger.error("获取权限列表失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("获取权限列表时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 获取所有权限（不分页）
     * @return 所有权限DTO列表
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<PermissionDTO>>> getAllPermissions() {
        try {
            logger.info("获取所有权限");
            List<PermissionDTO> permissions = permissionService.getAllPermissions();
            return ResponseEntity.ok(ResponseMessage.success(permissions));
        } catch (Exception e) {
            logger.error("获取所有权限时发生系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
}