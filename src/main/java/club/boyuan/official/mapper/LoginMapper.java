package club.boyuan.official.mapper;

import club.boyuan.official.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginMapper {
    User getUserByUsername(String username);
}