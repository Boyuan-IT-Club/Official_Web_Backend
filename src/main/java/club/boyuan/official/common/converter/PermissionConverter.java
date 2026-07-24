package club.boyuan.official.common.converter;

import club.boyuan.official.domain.user.dto.PermissionDTO;
import club.boyuan.official.persistence.entity.Permission;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Permission 与 PermissionDTO 之间的映射，由 MapStruct 在编译期生成实现。
 */
@Mapper(componentModel = "spring")
public interface PermissionConverter {

    Permission toEntity(PermissionDTO dto);

    PermissionDTO toDto(Permission entity);

    List<PermissionDTO> toDtoList(List<Permission> entities);
}
