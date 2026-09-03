package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.RecruitmentCycle;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 招募周期Mapper接口
 */
public interface RecruitmentCycleMapper extends BaseMapper<RecruitmentCycle> {
    
    /**
     * 根据ID查询招募周期
     * @param cycleId 招募周期ID
     * @return 招募周期实体
     */
    RecruitmentCycle findById(Integer cycleId);

    /**
     * 清空周期的招新提示语。仅供「无投递周期的物理删除」前置清理：
     * recruitment_tips 对 cycle 的外键没有 ON DELETE 动作（RESTRICT 语义），
     * 该表在 Java 侧没有独立 Mapper，就近挂在这里。
     */
    @Delete("DELETE FROM recruitment_tips WHERE cycle_id = #{cycleId}")
    int deleteTipsByCycleId(@Param("cycleId") Integer cycleId);
    
    /**
     * 查询所有招募周期
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findAll();
    
    /**
     * 根据状态查询招募周期
     * @param status 状态
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findByStatus(Integer status);
    
    /**
     * 根据是否启用查询招募周期
     * @param isActive 是否启用
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findByIsActive(Integer isActive);
    
    /**
     * 根据学年查询招募周期
     * @param academicYear 学年
     * @return 招募周期实体
     */
    RecruitmentCycle findByAcademicYear(String academicYear);
    
    /**
     * 软删除（单删与批删共用）：置 is_deleted = 1 并同时停用。
     * 没有硬删方法是刻意的 —— interview_session/interview_time_slot 对周期是
     * ON DELETE CASCADE，硬删会静默连带清掉整个周期的面试数据。
     * @return 实际置位的行数（已删除的行不重复计数）
     */
    int softDeleteByIds(@Param("cycleIds") List<Integer> cycleIds);
    
    /**
     * 批量更新招募周期
     * @param recruitmentCycles 招募周期列表
     * @return 影响行数
     */
    int batchUpdate(@Param("recruitmentCycles") List<RecruitmentCycle> recruitmentCycles);
    
    /**
     * 查询所有招募周期（支持分页和排序）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findAllWithPaginationAndSorting(@Param("offset") int offset, 
                                                           @Param("limit") int limit, 
                                                           @Param("sortBy") String sortBy, 
                                                           @Param("sortOrder") String sortOrder);
    
    /**
     * 根据条件查询招募周期（支持分页和排序）
     * @param cycleName 招募周期名称
     * @param academicYear 学年
     * @param status 状态
     * @param isActive 是否启用
     * @param offset 偏移量
     * @param limit 限制数量
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findByConditions(@Param("cycleName") String cycleName,
                                            @Param("academicYear") String academicYear,
                                            @Param("status") Integer status,
                                            @Param("isActive") Integer isActive,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit,
                                            @Param("sortBy") String sortBy,
                                            @Param("sortOrder") String sortOrder);
    
    /**
     * 统计符合条件的招募周期数量
     * @param cycleName 招募周期名称
     * @param academicYear 学年
     * @param status 状态
     * @param isActive 是否启用
     * @return 数量
     */
    long countByConditions(@Param("cycleName") String cycleName,
                           @Param("academicYear") String academicYear,
                           @Param("status") Integer status,
                           @Param("isActive") Integer isActive);
    
    /**
     * 根据当前日期更新招募周期状态
     * @param currentDate 当前日期
     * @return 影响行数
     */
    int updateStatusBasedOnDate(@Param("currentDate") LocalDate currentDate);

    /**
     * 当前开放投递的周期:启用中,且今天落在起止日期内。
     *
     * 刻意不看 status 列:它虽有 1未开始/2进行中/3已结束 的语义,但只有一个手动
     * 管理接口会刷新它(没有定时任务),实际数据长期陈旧。起止日期是管理端唯一
     * 真正维护的字段,所以以日期为权威。
     *
     * 按 start_date 倒序:同时开放多个时,较新的排前面做默认选中。
     */
    List<RecruitmentCycle> findOpenForApplication(@Param("today") LocalDate today);

    /**
     * 即将开放的周期:启用中,且开始日期还没到。
     *
     * 与 findOpenForApplication 一样以日期为权威,不看 status 列。
     *
     * 单独一个查询而不是并进「开放」里:openIds 在前端是「能不能投」的闸门,
     * 把未开始的混进去,用户就能给一个还没开始的周期提交简历。
     * 未开始的周期要在用户端可见(做预告),但可见与可投是两件事。
     *
     * 按 start_date 升序:最快开始的排最前,预告卡片的自然顺序。
     */
    List<RecruitmentCycle> findUpcomingForApplication(@Param("today") LocalDate today);
}