package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RecruitmentQrCodeMapper extends BaseMapper<RecruitmentQrCode>, ParentRecordMode {

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

    @Select("SELECT * FROM cycle_qr_entries WHERE CAST(id AS CHAR)=#{identifier} OR token=#{identifier}")
    RecruitmentQrCode findIdentifier(@Param("identifier") String identifier);

    @Update("""
        UPDATE recruitment_cycle SET qr_codes=JSON_SET(
            CASE WHEN #{dept} AND JSON_EXTRACT(qr_codes,'$.DEPT') IS NULL
                 THEN JSON_SET(COALESCE(qr_codes,JSON_OBJECT()),'$.DEPT',JSON_OBJECT())
                 ELSE COALESCE(qr_codes,JSON_OBJECT()) END, #{path},
            JSON_MERGE_PATCH(
                COALESCE(JSON_EXTRACT(qr_codes,#{path}),JSON_OBJECT('token',#{token},'createdAt',UNIX_TIMESTAMP())),
                JSON_OBJECT('image',#{image},'updatedAt',UNIX_TIMESTAMP(),'remark',#{remark}))),
            updated_at=updated_at WHERE cycle_id=#{cycleId}
        """)
    void putCode(@Param("cycleId") int cycleId, @Param("path") String path, @Param("dept") boolean department,
                 @Param("token") String token, @Param("image") String image, @Param("remark") String remark);

    @Update("""
        UPDATE recruitment_cycle SET qr_codes=JSON_REMOVE(qr_codes,#{path}),updated_at=updated_at WHERE cycle_id=#{cycleId}
        """)
    void removeCode(@Param("cycleId") int cycleId,@Param("path") String path);

    List<RecruitmentQrCode> readCycleCodes(@Param("cycleId") int cycleId);
}
