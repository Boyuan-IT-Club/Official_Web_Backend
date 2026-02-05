package club.boyuan.official.mapper;

import club.boyuan.official.entity.InterviewResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * <p>
 * 面试结果表 Mapper 接口
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface InterviewResultMapper extends BaseMapper<InterviewResult> {

    Page<InterviewResult> selectResultPage(Page<InterviewResult> pageInfo, Integer cycleId, String name, String decision, String department);
}
