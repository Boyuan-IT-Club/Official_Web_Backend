package club.boyuan.official.service.impl;

import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.User;
import club.boyuan.official.entity.UserRole;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.RoleMapper;
import club.boyuan.official.mapper.UserMapper;
import club.boyuan.official.mapper.UserRoleMapper;
import club.boyuan.official.service.UserRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * UserRole的业务层实现
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 20:41
 * @since 2026
 */
@Service
@AllArgsConstructor
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {

    private static final Logger logger = LoggerFactory.getLogger(UserRoleServiceImpl.class);

    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Role> assignRoles(int userId, List<Integer> roleIds) throws BusinessException {
        if (userId <= 0 || roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "用户ID和角色ID列表不能为空");
        }
        
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "用户不存在");
        }
        
        // 检查角色是否存在
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "部分角色不存在");
        }
        
        // 先删除用户原有的所有角色
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
        
        // 批量添加新角色
        List<UserRole> userRoles = new ArrayList<>();
        for (Integer roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoles.add(userRole);
        }
        
        // 批量插入，使用saveBatch方法
        saveBatch(userRoles);
        logger.info("成功为用户分配角色，用户ID: {}, 分配角色数量: {}", userId, roleIds.size());
        
        return roles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRole addRoleToUser(int userId, int roleId) throws BusinessException {
        if (userId <= 0 || roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "用户ID和角色ID不能为空");
        }
        
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "用户不存在");
        }
        
        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 检查用户是否已经拥有该角色
        UserRole existingUserRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId));
        if (existingUserRole != null) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "用户已经拥有该角色");
        }
        
        // 添加角色
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);
        
        logger.info("成功为用户添加角色，用户ID: {}, 角色ID: {}", userId, roleId);
        return userRole;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeRoleFromUser(int userId, int roleId) throws BusinessException {
        if (userId <= 0 || roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "用户ID和角色ID不能为空");
        }
        
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "用户不存在");
        }
        
        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 删除角色关联
        int result = userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId));
        
        boolean success = result > 0;
        if (success) {
            logger.info("成功从用户移除角色，用户ID: {}, 角色ID: {}", userId, roleId);
        }
        
        return success;
    }

    @Override
    public List<Role> getRolesByUserId(int userId) throws BusinessException {
        if (userId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "用户ID不能为空");
        }
        
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "用户不存在");
        }
        
        // 查询用户的角色ID列表
        List<Integer> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
        
        if (roleIds.isEmpty()) {
            return List.of();
        }
        
        // 查询角色详情
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        logger.info("获取用户角色列表成功，用户ID: {}, 角色数量: {}", userId, roles.size());
        return roles;
    }

    @Override
    public List<User> getUsersByRoleId(int roleId, int page, int size) throws BusinessException {
        if (roleId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "角色ID不能为空");
        }
        
        // 检查角色是否存在
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "角色不存在");
        }
        
        // 查询拥有该角色的用户ID列表
        List<Integer> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, roleId))
                .stream()
                .map(UserRole::getUserId)
                .toList();
        
        if (userIds.isEmpty()) {
            return List.of();
        }
        
        // 分页查询用户详情
        Page<User> pageObj = new Page<>(page, size);
        List<User> users = userMapper.selectPage(pageObj, new LambdaQueryWrapper<User>()
                .in(User::getUserId, userIds)
                .orderByAsc(User::getUsername))
                .getRecords();
        
        logger.info("获取角色用户列表成功，角色ID: {}, 页码: {}, 每页大小: {}, 用户数量: {}", 
                roleId, page, size, users.size());
        return users;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAssignRoles(List<Integer> userIds, List<Integer> roleIds) throws BusinessException {
        if (userIds == null || userIds.isEmpty() || roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "用户ID列表和角色ID列表不能为空");
        }
        
        // 检查用户是否存在
        List<User> users = userMapper.selectByIds(userIds);
        if (users.size() != userIds.size()) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "部分用户不存在");
        }
        
        // 检查角色是否存在
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(BusinessExceptionEnum.ROLE_NOT_FOUND, "部分角色不存在");
        }
        
        // 批量创建用户角色关系
        List<UserRole> userRoles = new ArrayList<>();
        for (Integer userId : userIds) {
            for (Integer roleId : roleIds) {
                // 检查是否已存在该关系
                if (userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId)) == null) {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(roleId);
                    userRoles.add(userRole);
                }
            }
        }
        
        if (!userRoles.isEmpty()) {
            // 批量插入，使用saveBatch方法
            saveBatch(userRoles);
        }
        
        logger.info("批量分配角色成功，用户数量: {}, 角色数量: {}, 新增关系数量: {}", 
                userIds.size(), roleIds.size(), userRoles.size());
        
        return userIds.size();
    }

    @Override
    public boolean checkUserHasRole(int userId, String roleCode) throws BusinessException {
        if (userId <= 0 || StringUtils.isBlank(roleCode)) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "用户ID和角色编码不能为空");
        }
        
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "用户不存在");
        }
        
        // 查询角色ID
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode));
        if (role == null) {
            return false;
        }
        
        // 检查用户是否拥有该角色
        UserRole userRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getRoleId()));
        
        boolean hasRole = userRole != null;
        logger.debug("检查用户角色，用户ID: {}, 角色编码: {}, 结果: {}", userId, roleCode, hasRole);
        return hasRole;
    }
}