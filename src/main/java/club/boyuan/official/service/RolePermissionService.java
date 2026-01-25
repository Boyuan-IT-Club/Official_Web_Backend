package club.boyuan.official.service;

import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.RolePermission;
import club.boyuan.official.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * RolePermission的业务层
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 19:31
 * @since 2026
 */
public interface RolePermissionService extends IService<RolePermission> {
    /**
     * 分配权限给角色
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 是否分配成功
     * @throws BusinessException 业务异常
     */
    boolean assignPermissions(int roleId, List<Integer> permissionIds) throws BusinessException;
    
    /**
     * 从角色中移除权限
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 是否移除成功
     * @throws BusinessException 业务异常
     */
    boolean removePermissions(int roleId, List<Integer> permissionIds) throws BusinessException;
    
    /**
     * 批量分配权限
     * @param rolePermissions 角色权限关系列表
     * @return 是否分配成功
     * @throws BusinessException 业务异常
     */
    boolean batchAssignPermissions(List<RolePermission> rolePermissions) throws BusinessException;
    
    /**
     * 查询角色拥有的权限ID列表
     * @param roleId 角色ID
     * @return 权限ID列表
     * @throws BusinessException 业务异常
     */
    List<Integer> getPermissionIdsByRoleId(int roleId) throws BusinessException;
    
    /**
     * 为角色添加单个权限
     * @param roleId 角色ID
     * @param permissionId 权限ID
     * @return 是否添加成功
     * @throws BusinessException 业务异常
     */
    boolean addPermission(int roleId, int permissionId) throws BusinessException;
    
    /**
     * 查询拥有某个权限的角色列表
     * @param permissionId 权限ID
     * @return 角色列表
     * @throws BusinessException 业务异常
     */
    List<Role> getRolesByPermissionId(int permissionId) throws BusinessException;
}