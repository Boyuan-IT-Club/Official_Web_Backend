package club.boyuan.official.mapper;

import club.boyuan.official.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginMapper extends BaseMapper<User> {
    User getUserByUsername(String username);
}