package club.boyuan.official.mapper;

import club.boyuan.official.entity.User;
import org.apache.ibatis.annotations.Mapper;
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

    List<User> findByRoleAndDeptAndStatus(String role, String dept, String status, Pageable pageable);

    long countByRoleAndDeptAndStatus(String role, String dept, String status);

    List<User> selectAll();
}