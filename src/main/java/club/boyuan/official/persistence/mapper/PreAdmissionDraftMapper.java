package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PreAdmissionDraftMapper extends BaseMapper<PreAdmissionDraft> {

    Page<PreAdmissionDraft> selectDraftPage(Page<PreAdmissionDraft> page,
                                             @Param("cycleId") Integer cycleId,
                                             @Param("name") String name,
                                             @Param("department") String department);

    List<Map<String, Object>> selectDepartmentStats(@Param("cycleId") Integer cycleId);

    int upsertBatch(@Param("cycleId") Integer cycleId,
                    @Param("resultIds") List<Integer> resultIds,
                    @Param("assignedDeptId") Integer assignedDeptId,
                    @Param("operatorId") Integer operatorId);

    int deleteBatch(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> resultIds);

    List<PreAdmissionDraft> selectForFinalize(@Param("cycleId") Integer cycleId);

    int publishToResults(@Param("cycleId") Integer cycleId, @Param("operatorId") Integer operatorId);
}
