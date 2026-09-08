package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.PreAdmissionBatchRequestDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionListResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionMutationResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionRemoveRequestDTO;
import club.boyuan.official.domain.interview.service.PreAdmissionService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.persistence.mapper.PreAdmissionDraftMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreAdmissionServiceImpl implements PreAdmissionService {

    private final PreAdmissionDraftMapper draftMapper;
    private final InterviewResultMapper resultMapper;
    private final DepartmentMapper departmentMapper;
    private final IUserService userService;

    @Override
    public PreAdmissionListResponseDTO list(Integer cycleId, String name, String department,
                                            Integer page, Integer size) {
        if (cycleId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "招募周期不能为空");
        }
        Page<PreAdmissionDraft> result = draftMapper.selectDraftPage(
                new Page<>(page, size), cycleId, name, department);
        return new PreAdmissionListResponseDTO(result.getTotal(), result.getRecords(),
                draftMapper.selectDepartmentStats(cycleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreAdmissionMutationResponseDTO save(PreAdmissionBatchRequestDTO request) {
        Department department = departmentMapper.selectById(request.getAssignedDeptId());
        if (department == null || !Integer.valueOf(1).equals(department.getStatus())) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND, "拟录取部门不存在或已停用");
        }

        List<Integer> requested = distinct(request.getResultIds());
        List<Integer> valid = resultMapper.selectPendingResultIdsInCycle(request.getCycleId(), requested);
        List<Integer> skipped = difference(requested, valid);
        if (!valid.isEmpty()) {
            draftMapper.upsertBatch(request.getCycleId(), valid, request.getAssignedDeptId(), currentUserId());
        }
        log.info("更新预录取名单，cycleId={}，deptId={}，有效 {} 人，跳过 {} 人",
                request.getCycleId(), request.getAssignedDeptId(), valid.size(), skipped.size());
        return new PreAdmissionMutationResponseDTO(valid.size(), skipped);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreAdmissionMutationResponseDTO remove(PreAdmissionRemoveRequestDTO request) {
        List<Integer> requested = distinct(request.getResultIds());
        int deleted = draftMapper.deleteBatch(request.getCycleId(), requested);
        return new PreAdmissionMutationResponseDTO(deleted, List.of());
    }

    /**
     * 一键转正只写最终结果，不发邮件。事务成功后，原有通知和结果名册接口会自然读到新结果。
     * 任意候选人已被其他入口定稿时整批回滚，避免悄悄覆盖或发布半份名单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finalizeCycle(Integer cycleId) {
        if (cycleId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "招募周期不能为空");
        }
        List<PreAdmissionDraft> drafts = draftMapper.selectForFinalize(cycleId);
        if (drafts.isEmpty()) {
            return 0;
        }
        int published = draftMapper.publishToResults(cycleId, currentUserId());
        if (published != drafts.size()) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT,
                    "预录取名单中有候选人已被定稿，请刷新名单后重试");
        }
        draftMapper.deleteBatch(cycleId, drafts.stream().map(PreAdmissionDraft::getResultId).toList());
        log.info("预录取名单转正完成，cycleId={}，发布 {} 人", cycleId, published);
        return published;
    }

    private List<Integer> distinct(List<Integer> ids) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(ids));
    }

    private List<Integer> difference(List<Integer> requested, List<Integer> valid) {
        Set<Integer> validSet = new HashSet<>(valid);
        return requested.stream().filter(id -> !validSet.contains(id)).toList();
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String username = principal instanceof UserDetails details ? details.getUsername()
                : principal instanceof String value ? value : null;
        if (username == null) {
            return null;
        }
        User user = userService.getUserByUsername(username);
        return user == null ? null : user.getUserId();
    }
}
