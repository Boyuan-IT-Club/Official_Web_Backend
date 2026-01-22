package club.boyuan.official.service;


import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import club.boyuan.official.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * Role的业务层
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 19:31
 * @since 2026
 */

public interface RoleService extends IService<Role> {
   /**
    * 创建角色
    * @param role 角色对象
    * @return 创建成功的角色对象
    * @throws BusinessException 业务异常
    */
   public Role createRole(Role role) throws BusinessException;
   
   /**
    * 更新角色
    * @param role 角色对象
    * @return 更新后的角色对象
    * @throws BusinessException 业务异常
    */
   public Role updateRole(Role role) throws BusinessException;
   
   /**
    * 删除角色（逻辑删除）
    * @param roleId 角色ID
    * @return 是否删除成功
    * @throws BusinessException 业务异常
    */
   public boolean deleteRole(int roleId) throws BusinessException;
   
   /**
    * 根据ID获取角色
    * @param roleId 角色ID
    * @return 角色对象
    * @throws BusinessException 业务异常
    */
   public Role getRoleById(int roleId) throws BusinessException;
   
   /**
    * 多条件查询角色列表
    * @param status 状态
    * @param keyword 关键字
    * @param page 页码
    * @param size 每页大小
    * @param sort 排序字段
    * @return 角色列表
    * @throws BusinessException 业务异常
    */
   public List<Role> getRoles(int status, String keyword,
       int page, int size, String sort) throws BusinessException; // 多条件查询：status,keyword,page,size,sort
   
   /**
    * 获取所有可用角色
    * @return 角色列表
    */
   public List<Role> getAllAvailableRoles();
   
   /**
    * 根据角色ID获取权限列表
    * @param roleId 角色ID
    * @return 权限列表
    * @throws BusinessException 业务异常
    */
   public List<Permission> getPermissionsByRoleId(int roleId) throws BusinessException;

}
