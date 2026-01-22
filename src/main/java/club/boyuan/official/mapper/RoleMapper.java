package club.boyuan.official.mapper;


import club.boyuan.official.entity.Permission;
import club.boyuan.official.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: Role的mapper层
 * @email "Zewang0217@outlook.com"
 * @date 2026/01/22 19:24
 */

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    
    /**
     * 根据角色ID查询权限列表
     * @param roleId 角色ID
     * @return 权限列表
     */
    @Select("SELECT p.* FROM permission p " +
            "JOIN role_permission rp ON p.permission_id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId}")
    List<Permission> selectPermissionsByRoleId(int roleId);

}
