package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.PreAdmissionBatchRequestDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionListResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionMutationResponseDTO;
import club.boyuan.official.domain.interview.dto.PreAdmissionRemoveRequestDTO;
import club.boyuan.official.domain.interview.service.PreAdmissionService;
import club.boyuan.official.domain.interview.service.ResultRosterService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.InterviewResultChange;
import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewResultChangeMapper;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreAdmissionServiceImpl implements PreAdmissionService {

    private final PreAdmissionDraftMapper draftMapper;
    private final InterviewResultMapper resultMapper;
    private final DepartmentMapper departmentMapper;
    private final IUserService userService;
    private final ResultRosterService rosterService;
    private final InterviewResultChangeMapper changeMapper;

    @Override
    public PreAdmissionListResponseDTO list(Integer cycleId, String name, String department,
                                            Integer page, Integer size) {
        if (cycleId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "招募周期不能为空");
        }
        rosterService.ensure(cycleId);
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
        // 已有结果的人发布不了：先点名，别等发布到一半条数对不上才整批回滚、只给一句「请刷新」
        List<String> conflicts = draftMapper.selectConflictNames(cycleId);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT,
                    "以下同学的结果已经不是待定，转正不了，请先把他们移出预录取名单：" + String.join("、", conflicts));
        }
        List<Integer> resultIds = drafts.stream().map(PreAdmissionDraft::getResultId).toList();
        Map<Integer, InterviewResult> before = resultMapper.selectBatchIds(resultIds).stream()
                .collect(Collectors.toMap(InterviewResult::getResultId, r -> r));
        Integer operator = currentUserId();
        int published = draftMapper.publishToResults(cycleId, operator);
        if (published != drafts.size()) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT,
                    "预录取名单中有候选人的结果刚被改动，请刷新名单后重试");
        }
        // 转正同样留痕：谁、何时把哪些人从待定转成了录取到哪个部门
        for (PreAdmissionDraft draft : drafts) {
            InterviewResult prev = before.get(draft.getResultId());
            changeMapper.insert(new InterviewResultChange()
                    .setResultId(draft.getResultId())
                    .setCycleId(cycleId)
                    .setAction(InterviewResultChange.ACTION_FINALIZE)
                    .setFromDecision(prev != null ? prev.getDecision() : 0)
                    .setFromDeptId(prev != null ? prev.getAssignedDeptId() : null)
                    .setToDecision(1)
                    .setToDeptId(draft.getAssignedDeptId())
                    .setWasNotified(prev != null && prev.getNotifiedAt() != null ? 1 : 0)
                    .setOperatorId(operator));
        }
        draftMapper.deleteBatch(cycleId, resultIds);
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
