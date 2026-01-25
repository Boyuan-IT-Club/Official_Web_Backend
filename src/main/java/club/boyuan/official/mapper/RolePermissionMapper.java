package club.boyuan.official.mapper;

import club.boyuan.official.entity.RolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
    
    /**
     * 查询角色拥有的权限ID列表
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @Select("SELECT permission_id FROM role_permission WHERE role_id = #{roleId}")
    List<Integer> selectPermissionIdsByRoleId(int roleId);
    
    /**
     * 查询拥有某个权限的角色列表
     * @param permissionId 权限ID
     * @return 角色ID列表
     */
    @Select("SELECT role_id FROM role_permission WHERE permission_id = #{permissionId}")
    List<Integer> selectRoleIdsByPermissionId(int permissionId);
}
