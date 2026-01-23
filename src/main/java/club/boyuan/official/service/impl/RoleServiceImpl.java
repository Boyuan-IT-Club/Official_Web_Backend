package club.boyuan.official.service.impl;


import club.boyuan.official.dto.PermissionDTO;
import club.boyuan.official.dto.RoleDTO;
import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.RoleMapper;
import club.boyuan.official.mapper.RolePermissionMapper;
import club.boyuan.official.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

/**
 * Role的业务层实现
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 19:42
 * @since 2026
 */

@Service
@AllArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    private RolePermissionMapper rolePermissionMapper;

    @Override
    public RoleDTO createRole(RoleDTO roleDTO) throws BusinessException {
        // 参数校验
        if (roleDTO == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色对象不能为空");
        }
        if (StringUtils.isBlank(roleDTO.getRoleName())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色名称不能为空");
        }
        if (StringUtils.isBlank(roleDTO.getRoleCode())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色编码不能为空");
        }

        // 转换DTO为实体
        Role role = new Role();
        BeanUtils.copyProperties(roleDTO, role);

        // 设置默认值
        if (role.getStatus() == null) {
            role.setStatus(1); // 默认启用
        }
        role.setUpdateTime(LocalDateTime.now());
        role.setCreateTime(LocalDateTime.now());
        
        // 插入数据
        baseMapper.insert(role);
        
        // 转换实体为DTO并返回
        RoleDTO resultDTO = new RoleDTO();
        BeanUtils.copyProperties(role, resultDTO);
        return resultDTO;
    }
    
    @Override
    public RoleDTO updateRole(RoleDTO roleDTO) throws BusinessException {
        if (roleDTO == null || roleDTO.getRoleId() == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        // 检查角色是否存在
        Role existingRole = baseMapper.selectById(roleDTO.getRoleId());
        if (existingRole == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 转换DTO为实体
        Role role = new Role();
        BeanUtils.copyProperties(roleDTO, role);
        
        // 更新时间
        role.setUpdateTime(LocalDateTime.now());
        role.setCreateTime(existingRole.getCreateTime()); // 保留创建时间
        
        // 更新数据
        baseMapper.updateById(role);
        
        // 转换实体为DTO并返回
        Role updatedRole = baseMapper.selectById(role.getRoleId());
        RoleDTO resultDTO = new RoleDTO();
        BeanUtils.copyProperties(updatedRole, resultDTO);
        return resultDTO;
    }
    
    @Override
    public boolean deleteRole(int roleId) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        // 检查角色是否存在
        Role existingRole = baseMapper.selectById(roleId);
        if (existingRole == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 逻辑删除（这里假设角色表有is_deleted字段，否则需要调整）
        Role updateRole = new Role();
        updateRole.setRoleId(roleId);
        updateRole.setStatus(0); // 标记为禁用
        updateRole.setUpdateTime(LocalDateTime.now());
        
        int result = baseMapper.updateById(updateRole);
        return result > 0;
    }
    
    @Override
    public RoleDTO getRoleById(int roleId) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        Role role = baseMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 转换实体为DTO并返回
        RoleDTO resultDTO = new RoleDTO();
        BeanUtils.copyProperties(role, resultDTO);
        return resultDTO;
    }
    
    @Override
    public List<RoleDTO> getRoles(int status, String keyword,
        int page, int size, String sort) throws BusinessException {
        // 创建Lambda查询包装器
        LambdaQueryWrapper<Role> queryWrapper = new LambdaQueryWrapper<>();
        
        // 状态条件（0表示查询所有状态）
        if (status != 0) {
            queryWrapper.eq(Role::getStatus, status);
        }
        
        // 关键字条件（模糊匹配角色名称和角色编码）
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                .like(Role::getRoleName, keyword)
                .or()
                .like(Role::getRoleCode, keyword)
            );
        }
        
        // 排序处理
        if (StringUtils.isNotBlank(sort)) {
            if (sort.startsWith("-")) {
                // 倒序
                String column = sort.substring(1);
                switch (column) {
                    case "roleName" -> queryWrapper.orderByDesc(Role::getRoleName);
                    case "roleCode" -> queryWrapper.orderByDesc(Role::getRoleCode);
                    case "createTime" -> queryWrapper.orderByDesc(Role::getCreateTime);
                    default -> queryWrapper.orderByDesc(Role::getCreateTime);
                }
            } else {
                // 正序
                switch (sort) {
                    case "roleName" -> queryWrapper.orderByAsc(Role::getRoleName);
                    case "roleCode" -> queryWrapper.orderByAsc(Role::getRoleCode);
                    case "createTime" -> queryWrapper.orderByAsc(Role::getCreateTime);
                    default -> queryWrapper.orderByAsc(Role::getCreateTime);
                }
            }
        } else {
            // 默认按创建时间倒序
            queryWrapper.orderByDesc(Role::getCreateTime);
        }
        
        // 分页处理
        Page<Role> pageObj = new Page<>(page, size);
        baseMapper.selectPage(pageObj, queryWrapper);
        
        // 转换实体列表为DTO列表并返回
        return pageObj.getRecords().stream().map(role -> {
            RoleDTO roleDTO = new RoleDTO();
            BeanUtils.copyProperties(role, roleDTO);
            return roleDTO;
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<RoleDTO> getAllAvailableRoles() {
        List<Role> roles = baseMapper.selectList(new LambdaQueryWrapper<Role>()
            .eq(Role::getStatus, 1)
            .orderByAsc(Role::getRoleName));
        
        // 转换实体列表为DTO列表并返回
        return roles.stream().map(role -> {
            RoleDTO roleDTO = new RoleDTO();
            BeanUtils.copyProperties(role, roleDTO);
            return roleDTO;
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<PermissionDTO> getPermissionsByRoleId(int roleId) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        // 检查角色是否存在
        Role role = baseMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 调用自定义方法查询权限列表
        List<Permission> permissions = ((RoleMapper) baseMapper).selectPermissionsByRoleId(roleId);
        
        // 转换为PermissionDTO列表并返回
        return permissions.stream().map(permission -> {
            PermissionDTO permissionDTO = new PermissionDTO();
            BeanUtils.copyProperties(permission, permissionDTO);
            return permissionDTO;
        }).collect(Collectors.toList());
    }
}
