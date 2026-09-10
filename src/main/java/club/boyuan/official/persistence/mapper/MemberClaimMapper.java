package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.MemberClaim;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberClaimMapper extends BaseMapper<MemberClaim> {

    /** 管理端列表：带申请人账号、部门名与审批人姓名 */
    IPage<MemberClaim> selectClaimPage(IPage<MemberClaim> page,
                                       @Param("status") Integer status,
                                       @Param("keyword") String keyword);
}
