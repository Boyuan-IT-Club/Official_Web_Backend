package club.boyuan.official.common.converter;

import club.boyuan.official.domain.user.dto.RoleDTO;
import club.boyuan.official.persistence.entity.Role;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Role 与 RoleDTO 之间的映射，由 MapStruct 在编译期生成实现。
 */
@Mapper(componentModel = "spring")
public interface RoleConverter {

    Role toEntity(RoleDTO dto);

    RoleDTO toDto(Role entity);

    List<RoleDTO> toDtoList(List<Role> entities);
}
