package club.boyuan.official.service.impl;


import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.RoleMapper;
import club.boyuan.official.mapper.RolePermissionMapper;
import club.boyuan.official.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

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
    public Role createRole(Role role) throws BusinessException {
        // 参数校验
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色对象不能为空");
        }
        if (StringUtils.isBlank(role.getRoleName())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色名称不能为空");
        }
        if (StringUtils.isBlank(role.getRoleCode())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色编码不能为空");
        }

        // 设置默认值
        if (role.getStatus() == null) {
            role.setStatus(1); // 默认启用
        }
        role.setUpdateTime(LocalDateTime.now());
        role.setCreateTime(LocalDateTime.now());
        
        // 插入数据
        baseMapper.insert(role);
        return role;
    }
    
    @Override
    public Role updateRole(Role role) throws BusinessException {
        if (role == null || role.getRoleId() == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        // 检查角色是否存在
        Role existingRole = baseMapper.selectById(role.getRoleId());
        if (existingRole == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 更新时间
        role.setUpdateTime(LocalDateTime.now());
        
        // 更新数据
        baseMapper.updateById(role);
        return baseMapper.selectById(role.getRoleId());
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
    public Role getRoleById(int roleId) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        Role role = baseMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        return role;
    }
    
    @Override
    public List<Role> getRoles(int status, String keyword,
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
        
        return pageObj.getRecords();
    }
    
    @Override
    public List<Role> getAllAvailableRoles() {
        return baseMapper.selectList(new LambdaQueryWrapper<Role>()
            .eq(Role::getStatus, 1)
            .orderByAsc(Role::getRoleName));
    }
    
    @Override
    public List<Permission> getPermissionsByRoleId(int roleId) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        // 检查角色是否存在
        Role role = baseMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 调用自定义方法查询权限列表
        return ((RoleMapper) baseMapper).selectPermissionsByRoleId(roleId);
    }
}
