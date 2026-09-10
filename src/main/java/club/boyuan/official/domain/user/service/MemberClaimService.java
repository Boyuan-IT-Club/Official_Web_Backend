package club.boyuan.official.domain.user.service;

import club.boyuan.official.persistence.entity.MemberClaim;
import com.baomidou.mybatisplus.core.metadata.IPage;

/** 老社员认领：学生提交申请，管理员审批后置为社员并归部门。 */
public interface MemberClaimService {

    /** 提交认领申请。已是社员或已有待审申请时拒绝。 */
    MemberClaim submit(Integer userId, MemberClaim form);

    /** 本人最近一条申请；从未申请过返回 null。 */
    MemberClaim myLatest(Integer userId);

    IPage<MemberClaim> page(Integer status, String keyword, int page, int size);

    /** 通过：写回 user.is_member=1 与部门。 */
    MemberClaim approve(Integer claimId, Integer reviewerId, Integer deptId, String note);

    /** 驳回：申请人可补充材料后重新提交。 */
    MemberClaim reject(Integer claimId, Integer reviewerId, String note);

    /** 待审数量，管理端菜单角标用。 */
    long pendingCount();
}
