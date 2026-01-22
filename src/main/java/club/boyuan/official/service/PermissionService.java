package club.boyuan.official.service;

import club.boyuan.official.entity.Permission;
import club.boyuan.official.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Permission的业务层
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 20:31
 * @since 2026
 */
public interface PermissionService extends IService<Permission> {
    /**
     * 创建权限
     * @param permission 权限对象
     * @return 创建成功的权限对象
     * @throws BusinessException 业务异常
     */
    Permission createPermission(Permission permission) throws BusinessException;
    
    /**
     * 更新权限
     * @param permission 权限对象
     * @return 更新后的权限对象
     * @throws BusinessException 业务异常
     */
    Permission updatePermission(Permission permission) throws BusinessException;
    
    /**
     * 删除权限
     * @param permissionId 权限ID
     * @return 是否删除成功
     * @throws BusinessException 业务异常
     */
    boolean deletePermission(int permissionId) throws BusinessException;
    
    /**
     * 根据ID获取权限详情
     * @param permissionId 权限ID
     * @return 权限对象
     * @throws BusinessException 业务异常
     */
    Permission getPermissionById(int permissionId) throws BusinessException;
    
    /**
     * 根据权限编码获取权限
     * @param permissionCode 权限编码
     * @return 权限对象
     * @throws BusinessException 业务异常
     */
    Permission getPermissionByCode(String permissionCode) throws BusinessException;
    
    /**
     * 分页获取权限列表
     * @param resourceIdentifier 资源标识符
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @param sort 排序字段
     * @return 权限列表
     * @throws BusinessException 业务异常
     */
    List<Permission> getPermissions(String resourceIdentifier, String keyword, int page, int size, String sort) throws BusinessException;
    
    /**
     * 获取所有权限（不分页）
     * @return 权限列表
     */
    List<Permission> getAllPermissions();
}