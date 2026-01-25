package club.boyuan.official.utils;

import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.entity.User;

/**
 * 权限判断工具类
 * 用于实现RBAC权限控制
 */
public class PermissionUtils {

    /**
     * 判断用户是否拥有指定权限
     * @param user 用户对象
     * @param permissionCode 权限编码
     * @return 是否拥有权限
     */
    public static boolean hasPermission(User user, String permissionCode) {
        if (user == null || permissionCode == null || permissionCode.isEmpty()) {
            return false;
        }

        // 如果用户是管理员，直接返回true
        if (hasAdminRole(user)) {
            return true;
        }

        // 遍历用户的所有角色
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                // 遍历角色的所有权限
                if (role.getPermissions() != null) {
                    for (Permission permission : role.getPermissions()) {
                        // 检查权限编码是否匹配
                        if (permissionCode.equals(permission.getPermissionCode())) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * 判断用户是否为管理员角色
     * @param user 用户对象
     * @return 是否为管理员
     */
    public static boolean hasAdminRole(User user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }

        // 遍历用户的所有角色，检查是否有管理员角色
        for (Role role : user.getRoles()) {
            if (role != null && "ROLE_ADMIN".equals(role.getRoleCode())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否为用户本人
     * @param currentUser 当前用户
     * @param targetUserId 目标用户ID
     * @return 是否为本人
     */
    public static boolean isUserSelf(User currentUser, Integer targetUserId) {
        if (currentUser == null || targetUserId == null) {
            return false;
        }

        return currentUser.getUserId().equals(targetUserId);
    }

    /**
     * 判断用户是否可以访问目标用户的资源
     * 规则：管理员可以访问所有，普通用户只能访问自己的
     * @param currentUser 当前用户
     * @param targetUserId 目标用户ID
     * @return 是否可以访问
     */
    public static boolean canAccessUserResource(User currentUser, Integer targetUserId) {
        return hasAdminRole(currentUser) || isUserSelf(currentUser, targetUserId);
    }
}