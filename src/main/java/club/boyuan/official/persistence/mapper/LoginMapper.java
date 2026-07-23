package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginMapper extends BaseMapper<User> {
    User getUserByUsername(String username);
}