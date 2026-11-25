package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RecruitmentQrCodeMapper extends BaseMapper<RecruitmentQrCode> {

    @Select("SELECT cycle_id FROM recruitment_cycle WHERE cycle_id=#{cycleId} FOR UPDATE")
    Integer lockCycle(@Param("cycleId") int cycleId);

    @Select("SELECT EXISTS(SELECT 1 FROM department WHERE dept_id=#{deptId})")
    boolean departmentExists(@Param("deptId") int deptId);

    @Update("""
        UPDATE recruitment_cycle c LEFT JOIN cycle_qr_source q ON q.cycle_id=c.cycle_id
        SET c.qr_codes=COALESCE(q.qr_codes,JSON_OBJECT()), c.updated_at=c.updated_at
        WHERE c.cycle_id=#{cycleId}
        """)
    void refreshCycleCodes(@Param("cycleId") int cycleId);

    List<RecruitmentQrCode> readCycleCodes(@Param("cycleId") int cycleId);
}
