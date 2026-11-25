package club.boyuan.official.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface EvaluationScopeMapper {
    @Select("SELECT user_id FROM user WHERE username=#{username} AND status=1 AND is_deleted=0")
    Integer viewerId(String username);

    @Select("SELECT permission_code FROM evaluation_account_grant WHERE user_id=#{viewer} "
            + "UNION SELECT CONCAT('ROLE_',r.role_code) FROM role r JOIN user_role ur ON r.role_id=ur.role_id "
            + "JOIN user u ON u.user_id=ur.user_id WHERE u.user_id=#{viewer} AND u.is_deleted=0 AND u.status=1 AND r.status=1")
    List<String> materialGrants(int viewer);

    @Select("SELECT permission_code FROM evaluation_account_grant WHERE username=#{username} AND "
            + "permission_code IN ('interview:evaluate','interview:board:manage','resume:audit')")
    List<String> boardGrants(String username);

    @Select("SELECT locked FROM collab_doc WHERE cycle_id=#{cycleId} AND doc_name=CONCAT('eval-board:',cycle_id)")
    Integer boardLock(int cycleId);

    @Select("SELECT schedule_id FROM evaluation_board_candidate WHERE cycle_id=#{cycleId} ORDER BY schedule_id")
    List<Integer> roster(int cycleId);

    @Select("SELECT dimension_id FROM evaluation_dimension WHERE cycle_id=#{cycleId} ORDER BY dimension_id")
    List<Integer> dimensions(int cycleId);

    @Select("SELECT DISTINCT user_id FROM evaluation_account_grant WHERE permission_code='interview:evaluate' ORDER BY user_id")
    List<Integer> evaluatorIds();

    @Select("SELECT EXISTS(SELECT 1 FROM evaluation_account_grant g JOIN collab_doc d ON d.cycle_id=#{cycleId} "
            + "AND d.doc_name=CONCAT('eval-board:',d.cycle_id) WHERE g.user_id=#{viewer} AND g.permission_code='interview:evaluate')")
    boolean boardAccess(@Param("viewer") int viewer, @Param("cycleId") int cycleId);

    @Select("SELECT EXISTS(SELECT 1 FROM evaluation_board_candidate WHERE cycle_id=#{cycleId} AND resume_id=#{resumeId} AND (#{scheduleId} IS NULL OR schedule_id=#{scheduleId}) AND (#{write}=FALSE OR locked=0))")
    boolean matchesSchedule(@Param("resumeId") int resumeId, @Param("cycleId") int cycleId,
                            @Param("scheduleId") Integer scheduleId, @Param("write") boolean write);

    @Select("SELECT EXISTS(SELECT 1 FROM evaluation_board_candidate c JOIN evaluation_account_grant g "
            + "ON g.user_id=#{viewer} AND g.permission_code='interview:evaluate' WHERE c.resume_id=#{resumeId} "
            + "AND (#{cycleId} IS NULL OR c.cycle_id=#{cycleId}) AND (#{scheduleId} IS NULL OR c.schedule_id=#{scheduleId}) "
            + "AND (#{owner} IS NULL OR c.user_id=#{owner}) AND (#{write}=FALSE OR c.locked=0))")
    boolean candidate(@Param("viewer") Integer viewer, @Param("resumeId") Integer resumeId,
                      @Param("cycleId") Integer cycleId, @Param("scheduleId") Integer scheduleId,
                      @Param("owner") Integer owner, @Param("write") boolean write);
}
