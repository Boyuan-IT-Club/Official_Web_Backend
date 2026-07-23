package club.boyuan.official.common.converter;

import club.boyuan.official.domain.user.dto.DepartmentDTO;
import club.boyuan.official.persistence.entity.Department;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Department 与 DepartmentDTO 之间的映射。
 * <p>
 * 由 MapStruct 在编译期生成实现（componentModel=spring，可直接注入），
 * 替换运行时反射的 {@code BeanUtils.copyProperties}：类型安全、字段拼错编译期报错、零反射开销。
 */
@Mapper(componentModel = "spring")
public interface DepartmentConverter {

    Department toEntity(DepartmentDTO dto);

    DepartmentDTO toDto(Department entity);

    List<DepartmentDTO> toDtoList(List<Department> entities);
}
