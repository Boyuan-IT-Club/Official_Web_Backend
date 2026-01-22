package club.boyuan.official.service;

import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.User;
import club.boyuan.official.entity.UserRole;
import club.boyuan.official.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * UserRole的业务层
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 20:41
 * @since 2026
 */
public interface UserRoleService extends IService<UserRole> {
    /**
     * 为用户分配角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 分配的角色列表
     * @throws BusinessException 业务异常
     */
    List<Role> assignRoles(int userId, List<Integer> roleIds) throws BusinessException;
    
    /**
     * 为用户添加单个角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 用户角色关系
     * @throws BusinessException 业务异常
     */
    UserRole addRoleToUser(int userId, int roleId) throws BusinessException;
    
    /**
     * 从用户移除角色
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 是否移除成功
     * @throws BusinessException 业务异常
     */
    boolean removeRoleFromUser(int userId, int roleId) throws BusinessException;
    
    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色列表
     * @throws BusinessException 业务异常
     */
    List<Role> getRolesByUserId(int userId) throws BusinessException;
    
    /**
     * 获取拥有指定角色的用户列表
     * @param roleId 角色ID
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表
     * @throws BusinessException 业务异常
     */
    List<User> getUsersByRoleId(int roleId, int page, int size) throws BusinessException;
    
    /**
     * 批量分配角色给多个用户
     * @param userIds 用户ID列表
     * @param roleIds 角色ID列表
     * @return 成功分配的用户数量
     * @throws BusinessException 业务异常
     */
    int batchAssignRoles(List<Integer> userIds, List<Integer> roleIds) throws BusinessException;
    
    /**
     * 检查用户是否拥有指定角色
     * @param userId 用户ID
     * @param roleCode 角色编码
     * @return 是否拥有该角色
     * @throws BusinessException 业务异常
     */
    boolean checkUserHasRole(int userId, String roleCode) throws BusinessException;
}