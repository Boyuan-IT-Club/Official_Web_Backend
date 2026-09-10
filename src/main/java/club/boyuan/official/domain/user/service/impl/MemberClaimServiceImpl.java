package club.boyuan.official.domain.user.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.user.service.MemberClaimService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.MemberClaim;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.MemberClaimMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberClaimServiceImpl implements MemberClaimService {

    private final MemberClaimMapper claimMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public MemberClaim submit(Integer userId, MemberClaim form) {
        if (userId == null || form == null || !StringUtils.hasText(form.getRealName())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请填写真实姓名");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        // 已经是社员就没有认领的必要——多半是重复点了入口
        if (Boolean.TRUE.equals(user.getIsMember())) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "你已经是社员，无需重复认领");
        }
        Long pending = claimMapper.selectCount(new LambdaQueryWrapper<MemberClaim>()
                .eq(MemberClaim::getUserId, userId)
                .eq(MemberClaim::getStatus, MemberClaim.STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "你已有一份待审核的认领申请，请耐心等待");
        }
        if (form.getDeptId() != null && departmentMapper.selectById(form.getDeptId()) == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND);
        }

        MemberClaim claim = new MemberClaim()
                .setUserId(userId)
                .setRealName(form.getRealName().trim())
                .setStudentId(trimOrNull(form.getStudentId()))
                .setJoinYear(trimOrNull(form.getJoinYear()))
                .setDeptId(form.getDeptId())
                .setEvidence(trimOrNull(form.getEvidence()))
                .setStatus(MemberClaim.STATUS_PENDING)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        claimMapper.insert(claim);
        log.info("老社员认领申请已提交，userId={}, claimId={}", userId, claim.getClaimId());
        return claim;
    }

    @Override
    public MemberClaim myLatest(Integer userId) {
        if (userId == null) {
            return null;
        }
        return claimMapper.selectOne(new LambdaQueryWrapper<MemberClaim>()
                .eq(MemberClaim::getUserId, userId)
                .orderByDesc(MemberClaim::getClaimId)
                .last("LIMIT 1"));
    }

    @Override
    public IPage<MemberClaim> page(Integer status, String keyword, int page, int size) {
        return claimMapper.selectClaimPage(new Page<>(page, size), status, keyword);
    }

    @Override
    @Transactional
    public MemberClaim approve(Integer claimId, Integer reviewerId, Integer deptId, String note) {
        MemberClaim claim = requirePending(claimId);
        // 审批时可以纠正申请人填错的部门；没传就沿用申请里的
        Integer finalDept = deptId != null ? deptId : claim.getDeptId();
        if (finalDept != null) {
            Department dept = departmentMapper.selectById(finalDept);
            if (dept == null) {
                throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND);
            }
        }

        /*
         * 只更新社员标记与部门两列，不走 updateById(整实体)——
         * 那会把审批窗口里用户自己改过的资料（头像、GitHub 等）按旧值写回。
         */
        LambdaUpdateWrapper<User> userUpdate = new LambdaUpdateWrapper<User>()
                .eq(User::getUserId, claim.getUserId())
                .set(User::getIsMember, true);
        if (finalDept != null) {
            userUpdate.set(User::getDeptId, finalDept);
        }
        userMapper.update(null, userUpdate);

        claim.setStatus(MemberClaim.STATUS_APPROVED)
                .setDeptId(finalDept)
                .setReviewNote(trimOrNull(note))
                .setReviewedBy(reviewerId)
                .setReviewedAt(LocalDateTime.now());
        claimMapper.updateById(claim);
        log.info("老社员认领已通过，claimId={}, userId={}, deptId={}, 审批人={}",
                claimId, claim.getUserId(), finalDept, reviewerId);
        return claim;
    }

    @Override
    @Transactional
    public MemberClaim reject(Integer claimId, Integer reviewerId, String note) {
        MemberClaim claim = requirePending(claimId);
        claim.setStatus(MemberClaim.STATUS_REJECTED)
                .setReviewNote(trimOrNull(note))
                .setReviewedBy(reviewerId)
                .setReviewedAt(LocalDateTime.now());
        claimMapper.updateById(claim);
        log.info("老社员认领已驳回，claimId={}, 审批人={}", claimId, reviewerId);
        return claim;
    }

    @Override
    public long pendingCount() {
        Long n = claimMapper.selectCount(new LambdaQueryWrapper<MemberClaim>()
                .eq(MemberClaim::getStatus, MemberClaim.STATUS_PENDING));
        return n == null ? 0 : n;
    }

    /** 审批只对待审申请生效——防止两个管理员同时点、后一个把结论覆盖成相反的 */
    private MemberClaim requirePending(Integer claimId) {
        MemberClaim claim = claimId == null ? null : claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BusinessException(BusinessExceptionEnum.MEMBER_CLAIM_NOT_FOUND);
        }
        if (!Integer.valueOf(MemberClaim.STATUS_PENDING).equals(claim.getStatus())) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT,
                    "该申请已被处理过，请刷新后查看");
        }
        return claim;
    }

    private static String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
