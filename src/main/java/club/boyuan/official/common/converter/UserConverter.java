package club.boyuan.official.common.converter;

import club.boyuan.official.domain.user.dto.UserDTO;
import club.boyuan.official.persistence.entity.User;
import org.mapstruct.Mapper;

/**
 * User 与 UserDTO 之间的映射。
 * <p>
 * 由 MapStruct 在编译期生成实现（componentModel=spring，可直接注入），
 * 替换运行时反射的 {@code BeanUtils.copyProperties}：类型安全、字段拼错编译期报错、零反射开销。
 * 仅映射同名字段（与原 BeanUtils 行为一致），User 上 DTO 未覆盖的字段（如角色、时间戳）不受影响。
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    User toEntity(UserDTO dto);

    UserDTO toDto(User entity);
}
