package club.boyuan.official.service.impl;

import club.boyuan.official.entity.Permission;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.PermissionMapper;
import club.boyuan.official.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Permission的业务层实现
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 20:31
 * @since 2026
 */
@Service
@AllArgsConstructor
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private final PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission createPermission(Permission permission) throws BusinessException {
        if (permission == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限对象不能为空");
        }
        
        // 验证必填字段
        if (StringUtils.isBlank(permission.getPermissionName())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限名称不能为空");
        }
        
        if (StringUtils.isBlank(permission.getPermissionCode())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限编码不能为空");
        }
        
        // 检查权限编码是否已存在
        if (permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermissionCode, permission.getPermissionCode())) != null) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "权限编码已存在");
        }
        
        // 保存权限
        permissionMapper.insert(permission);
        logger.info("成功创建权限，权限ID: {}, 权限名称: {}", permission.getPermissionId(), permission.getPermissionName());
        return permission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission updatePermission(Permission permission) throws BusinessException {
        if (permission == null || permission.getPermissionId() == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限ID不能为空");
        }
        
        // 检查权限是否存在
        Permission existingPermission = permissionMapper.selectById(permission.getPermissionId());
        if (existingPermission == null) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_NOT_FOUND, "权限不存在");
        }
        
        // 如果修改了权限编码，检查新编码是否已存在
        if (StringUtils.isNotBlank(permission.getPermissionCode()) && 
                !permission.getPermissionCode().equals(existingPermission.getPermissionCode())) {
            if (permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                    .eq(Permission::getPermissionCode, permission.getPermissionCode())) != null) {
                throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "权限编码已存在");
            }
        }
        
        // 更新权限
        permissionMapper.updateById(permission);
        logger.info("成功更新权限，权限ID: {}, 权限名称: {}", permission.getPermissionId(), permission.getPermissionName());
        return permissionMapper.selectById(permission.getPermissionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePermission(int permissionId) throws BusinessException {
        if (permissionId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限ID不能为空");
        }
        
        // 检查权限是否存在
        Permission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_NOT_FOUND, "权限不存在");
        }
        
        // 删除权限
        int result = permissionMapper.deleteById(permissionId);
        boolean success = result > 0;
        if (success) {
            logger.info("成功删除权限，权限ID: {}, 权限名称: {}", permissionId, permission.getPermissionName());
        }
        return success;
    }

    @Override
    public Permission getPermissionById(int permissionId) throws BusinessException {
        if (permissionId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限ID不能为空");
        }
        
        Permission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_NOT_FOUND, "权限不存在");
        }
        return permission;
    }

    @Override
    public Permission getPermissionByCode(String permissionCode) throws BusinessException {
        if (StringUtils.isBlank(permissionCode)) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "权限编码不能为空");
        }
        
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermissionCode, permissionCode));
        if (permission == null) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_NOT_FOUND, "权限不存在");
        }
        return permission;
    }

    @Override
    public List<Permission> getPermissions(String resourceIdentifier, String keyword, int page, int size, String sort) throws BusinessException {
        // 创建Lambda查询包装器
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        
        // 资源标识符条件
        if (StringUtils.isNotBlank(resourceIdentifier)) {
            queryWrapper.eq(Permission::getResourceIdentifier, resourceIdentifier);
        }
        
        // 关键字条件（模糊匹配权限名称或编码）
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Permission::getPermissionName, keyword)
                    .or()
                    .like(Permission::getPermissionCode, keyword));
        }
        
        // 排序处理
        if (StringUtils.isNotBlank(sort)) {
            if (sort.startsWith("-")) {
                // 倒序
                String column = sort.substring(1);
                switch (column) {
                    case "permissionName" -> queryWrapper.orderByDesc(Permission::getPermissionName);
                    case "permissionCode" -> queryWrapper.orderByDesc(Permission::getPermissionCode);
                    case "createTime" -> queryWrapper.orderByDesc(Permission::getCreateTime);
                    default -> queryWrapper.orderByDesc(Permission::getPermissionId);
                }
            } else {
                // 正序
                switch (sort) {
                    case "permissionName" -> queryWrapper.orderByAsc(Permission::getPermissionName);
                    case "permissionCode" -> queryWrapper.orderByAsc(Permission::getPermissionCode);
                    case "createTime" -> queryWrapper.orderByAsc(Permission::getCreateTime);
                    default -> queryWrapper.orderByAsc(Permission::getPermissionId);
                }
            }
        } else {
            // 默认按权限ID正序
            queryWrapper.orderByAsc(Permission::getPermissionId);
        }
        
        // 分页处理
        Page<Permission> pageObj = new Page<>(page, size);
        permissionMapper.selectPage(pageObj, queryWrapper);
        
        logger.info("查询权限列表成功，资源标识符: {}, 关键字: {}, 页码: {}, 每页大小: {}", 
                resourceIdentifier, keyword, page, size);
        return pageObj.getRecords();
    }

    @Override
    public List<Permission> getAllPermissions() {
        List<Permission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getPermissionName));
        logger.info("获取所有权限成功，共{}个权限", permissions.size());
        return permissions;
    }
}