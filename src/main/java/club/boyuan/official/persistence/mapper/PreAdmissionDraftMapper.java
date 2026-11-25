package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PreAdmissionDraftMapper {

    Page<PreAdmissionDraft> selectDraftPage(Page<PreAdmissionDraft> page,
                                             @Param("cycleId") Integer cycleId,
                                             @Param("name") String name,
                                             @Param("department") String department);

    List<Map<String, Object>> selectDepartmentStats(@Param("cycleId") Integer cycleId);

    int upsertBatch(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> ids,
                     @Param("assignedDeptId") Integer dept, @Param("operatorId") Integer actor);

    int deleteBatch(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> ids);

    List<PreAdmissionDraft> selectForFinalize(@Param("cycleId") Integer cycleId);

    long activeCount(@Param("cycleId") Integer cycleId);

    int publishToResults(@Param("cycleId") Integer cycleId, @Param("operatorId") Integer operatorId,
                         @Param("resultIds") List<Integer> resultIds);
}
