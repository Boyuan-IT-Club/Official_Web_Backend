package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.Activity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社团活动数据访问层
 */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
