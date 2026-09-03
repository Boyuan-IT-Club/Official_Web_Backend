package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewSessionDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 场次-部门关联。
 *
 * 不自定义查询方法：按 sessionId / deptId 捞，BaseMapper 的条件查询够用。
 * 另见 MapperBuiltinNameCollisionTest —— 自定义方法名撞上 BaseMapper 的
 * 集合方法会静默改变语义。
 */
@Mapper
public interface InterviewSessionDeptMapper extends BaseMapper<InterviewSessionDept> {
}
