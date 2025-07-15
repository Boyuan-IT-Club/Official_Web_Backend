package club.boyuan.official.mapper;

import club.boyuan.official.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Mapper
public interface UserMapper {
    int insert(User user);

    User selectById(Integer userId);

    User selectByUsername(String username);

    int updateById(User user);

    int deleteById(Integer userId);

    User selectByEmail(String email);

    User selectByPhone(String phone);

    /**
     * 根据角色、部门和状态分页查询用户
     *
     * @param role     角色
     * @param dept     部门
     * @param status   状态
     * @param pageable 分页信息
     * @return 用户分页数据
     */
    List<User> findByRoleAndDeptAndStatus(String role, String dept, String status, Pageable pageable);

    /**
     * 根据角色、部门和状态统计用户数量
     *
     * @param role   角色
     * @param dept   部门
     * @param status 状态
     * @return 用户数量
     */
    long countByRoleAndDeptAndStatus(String role, String dept, String status);

    List<User> selectAll();
}