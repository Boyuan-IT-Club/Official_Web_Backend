package club.boyuan.official.mapper;

import club.boyuan.official.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Mapper
public interface UserMapper {
    
    User selectById(Integer userId);

    User selectByUsername(String username);

    User selectByEmail(String email);

    User selectByPhone(String phone);

    int insert(User user);

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
}