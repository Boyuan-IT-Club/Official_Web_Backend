package club.boyuan.official.persistence.mapper;

import org.apache.ibatis.annotations.Select;

public interface ParentRecordMode {
    @Select("SELECT enabled FROM parent_record_cutover")
    boolean parentOnly();
}
