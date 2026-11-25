package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PreAdmissionDraftMapper extends BaseMapper<PreAdmissionDraft>, ParentRecordMode {

    Page<PreAdmissionDraft> selectDraftPage(Page<PreAdmissionDraft> page,
                                             @Param("cycleId") Integer cycleId,
                                             @Param("name") String name,
                                             @Param("department") String department);

    List<Map<String, Object>> selectDepartmentStats(@Param("cycleId") Integer cycleId);

    default int upsertBatch(Integer cycleId, List<Integer> ids, Integer dept, Integer actor) {
        return parentOnly() ? saveOnResult(cycleId, ids, dept, actor) : saveLegacy(cycleId, ids, dept, actor);
    }

    default int deleteBatch(Integer cycleId, List<Integer> ids) {
        return parentOnly() ? removeFromResult(cycleId, ids) : removeLegacy(cycleId, ids);
    }

    int saveOnResult(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> ids,
                     @Param("assignedDeptId") Integer dept, @Param("operatorId") Integer actor);

    int removeFromResult(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> ids);

    int saveLegacy(@Param("cycleId") Integer cycleId,
                    @Param("resultIds") List<Integer> resultIds,
                    @Param("assignedDeptId") Integer assignedDeptId,
                    @Param("operatorId") Integer operatorId);

    int removeLegacy(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> resultIds);

    List<PreAdmissionDraft> selectForFinalize(@Param("cycleId") Integer cycleId);

    long activeCount(@Param("cycleId") Integer cycleId);

    int publishToResults(@Param("cycleId") Integer cycleId, @Param("operatorId") Integer operatorId,
                         @Param("resultIds") List<Integer> resultIds);
}
