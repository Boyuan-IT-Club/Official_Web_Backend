package club.boyuan.official.service;

import club.boyuan.official.dto.PermissionDTO;
import club.boyuan.official.dto.RoleDTO;
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

import club.boyuan.official.dto.RoleDTO;

public interface RoleService extends IService<Role> {
   /**
    * 创建角色
    * @param roleDTO 角色DTO对象
    * @return 创建成功的角色DTO
    * @throws BusinessException 业务异常
    */
   public RoleDTO createRole(RoleDTO roleDTO) throws BusinessException;
   
   /**
    * 更新角色
    * @param roleDTO 角色DTO对象
    * @return 更新后的角色DTO
    * @throws BusinessException 业务异常
    */
   public RoleDTO updateRole(RoleDTO roleDTO) throws BusinessException;
   
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
    * @return 角色DTO
    * @throws BusinessException 业务异常
    */
   public RoleDTO getRoleById(int roleId) throws BusinessException;
   
   /**
    * 多条件查询角色列表
    * @param status 状态
    * @param keyword 关键字
    * @param page 页码
    * @param size 每页大小
    * @param sort 排序字段
    * @return 角色DTO列表
    * @throws BusinessException 业务异常
    */
   public List<RoleDTO> getRoles(int status, String keyword,
       int page, int size, String sort) throws BusinessException; // 多条件查询：status,keyword,page,size,sort
   
   /**
    * 获取所有可用角色
    * @return 角色DTO列表
    */
   public List<RoleDTO> getAllAvailableRoles();
   
   /**
    * 根据角色ID获取权限列表
    * @param roleId 角色ID
    * @return 权限DTO列表
    * @throws BusinessException 业务异常
    */
   public List<PermissionDTO> getPermissionsByRoleId(int roleId) throws BusinessException;

}
