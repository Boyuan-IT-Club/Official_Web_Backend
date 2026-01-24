package club.boyuan.official.service.impl;

import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.RolePermission;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.RoleMapper;
import club.boyuan.official.mapper.RolePermissionMapper;
import club.boyuan.official.service.RolePermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RolePermission的业务层实现
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 19:42
 * @since 2026
 */
@Service
@AllArgsConstructor
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements RolePermissionService {

    private RoleMapper roleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignPermissions(int roleId, List<Integer> permissionIds) throws BusinessException {
        if (roleId <= 0 || permissionIds == null || permissionIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID和权限ID列表不能为空");
        }

        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }

        // 先删除角色原有的所有权限
        baseMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));

        // 批量添加新的权限关系
        List<RolePermission> rolePermissions = new ArrayList<>();
        for (Integer permissionId : permissionIds) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissions.add(rolePermission);
        }

        return saveBatch(rolePermissions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePermissions(int roleId, List<Integer> permissionIds) throws BusinessException {
        if (roleId <= 0 || permissionIds == null || permissionIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID和权限ID列表不能为空");
        }

        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }

        // 删除指定的角色权限关系
        int result = baseMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId)
                .in(RolePermission::getPermissionId, permissionIds));

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchAssignPermissions(List<RolePermission> rolePermissions) throws BusinessException {
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色权限关系列表不能为空");
        }

        return saveBatch(rolePermissions);
    }

    @Override
    public List<Integer> getPermissionIdsByRoleId(int roleId) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }

        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }

        return baseMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addPermission(int roleId, int permissionId) throws BusinessException {
        if (roleId <= 0 || permissionId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID和权限ID不能为空");
        }

        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }

        // 检查权限是否已经存在
        RolePermission existing = baseMapper.selectOne(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getPermissionId, permissionId));
        if (existing != null) {
            // 权限已存在，返回true表示成功
            return true;
        }

        // 添加新的权限关系
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        return save(rolePermission);
    }

    @Override
    public List<Role> getRolesByPermissionId(int permissionId) throws BusinessException {
        if (permissionId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限ID不能为空");
        }

        // 查询拥有该权限的角色ID列表
        List<Integer> roleIds = baseMapper.selectRoleIdsByPermissionId(permissionId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询角色详情
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .in(Role::getRoleId, roleIds)
                .orderByAsc(Role::getRoleName));
    }
}