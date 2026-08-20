package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    User selectById(Integer userId);

    User selectByUsername(String username);

    User selectByEmail(String email);

    User selectByPhone(String phone);

    int updateById(User user);

    int updatePasswordById(@Param("userId") Integer userId, @Param("password") String password);

    int deleteById(Integer userId);
    
    List<User> selectAll();
    
    List<User> findByRoleAndDeptAndStatus(@Param("role") String role, 
                                          @Param("dept") String dept, 
                                          @Param("status") String status, 
                                          Pageable pageable);
                                          
    long countByRoleAndDeptAndStatus(@Param("role") String role, 
                                     @Param("dept") String dept, 
                                     @Param("status") String status);
    
    List<User> searchUsers(@Param("keyword") String keyword);
    
    /**
     * 批量更新用户状态
     * @param userIds 用户ID列表
     * @param status 状态值
     * @return 更新的记录数
     */
    int batchUpdateStatusByIds(@Param("userIds") List<Integer> userIds, @Param("status") Boolean status);
    
    /**
     * 批量更新用户部门
     * @param userIds 用户ID列表
     * @param dept 部门名称
     * @return 更新的记录数
     */
    int batchUpdateDeptByIds(@Param("userIds") List<Integer> userIds, @Param("dept") String dept);
    
    /**
     * 批量更新用户会员状态
     * @param userIds 用户ID列表
     * @param isMember 会员状态
     * @return 更新的记录数
     */
    int batchUpdateMembershipByIds(@Param("userIds") List<Integer> userIds, @Param("isMember") Boolean isMember);

    /**
     * 管理端统计卡用的全库计数(不随分页/筛选变化)。
     */
    long countByMembership(@Param("isMember") boolean isMember);

    long countFrozen();
    
    /**
     * 根据用户ID列表查询用户
     * @param userIds 用户ID列表
     * @return 用户列表
     */
    /**
     * 按 ID 批量查用户。
     *
     * 不能叫 selectByIds：MyBatis-Plus 3.5.9 的 BaseMapper 内置了同名方法
     * （参数名是 coll），而同名 XML 语句会顶掉内置实现 —— 于是任何走
     * IService.listByIds() 的调用（飞书导入就是）都会带着 coll 撞上这条
     * 要 userIds 的 XML，报 "Parameter 'userIds' not found"。
     * 线上飞书同步/拉取整条链路因此全挂。自定义方法名必须避开内置名。
     */
    List<User> selectUsersByIds(@Param("userIds") List<Integer> userIds);
}