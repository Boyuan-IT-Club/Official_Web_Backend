package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PreAdmissionDraftMapper extends BaseMapper<PreAdmissionDraft> {

    /** 名单里看得到的草稿数（排除初筛未通过且仍待定的人） */
    Long countVisibleDrafts(@Param("cycleId") Integer cycleId);

    /** 挂在已有结果的行上的草稿对应的同学姓名（转正会失败的那些人） */
    List<String> selectConflictNames(@Param("cycleId") Integer cycleId);

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
