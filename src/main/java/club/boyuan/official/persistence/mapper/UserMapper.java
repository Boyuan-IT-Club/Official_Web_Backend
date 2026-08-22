package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

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
    
    List<User> findByRoleAndDeptAndStatus(@Param("roleGroup") String roleGroup,
                                          @Param("roleId") Integer roleId,
                                          @Param("dept") String dept,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    long countByRoleAndDeptAndStatus(@Param("roleGroup") String roleGroup,
                                     @Param("roleId") Integer roleId,
                                     @Param("dept") String dept,
                                     @Param("status") String status,
                                     @Param("keyword") String keyword);

    /** 用户分类统计：单条 SQL 聚合 total/frozen/adminCount/memberCount/nonMemberCount */
    Map<String, Object> countUserBuckets();
    
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
     * 根据用户ID列表查询用户
     * @param userIds 用户ID列表
     * @return 用户列表
     */
    List<User> selectByIds(@Param("userIds") List<Integer> userIds);
}