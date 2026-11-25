package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.MemberClaim;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface MemberClaimMapper extends BaseMapper<MemberClaim> {

    @Select("SELECT * FROM member_claim WHERE claim_id=#{id} FOR UPDATE")
    MemberClaim lockForReview(@Param("id") int id);

    @Insert("""
            INSERT INTO user_role(user_id,role_id)
            VALUES (#{userId}, (SELECT role_id FROM role WHERE role_code='MEMBER'))
            ON DUPLICATE KEY UPDATE user_role_id=user_role_id
            """)
    int grantMemberRole(@Param("userId") int userId);

    /** 管理端列表：带申请人账号、部门名与审批人姓名 */
    IPage<MemberClaim> selectClaimPage(IPage<MemberClaim> page,
                                       @Param("status") Integer status,
                                       @Param("keyword") String keyword);
}
