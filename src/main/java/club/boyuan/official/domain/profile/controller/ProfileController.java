package club.boyuan.official.domain.profile.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.profile.IProfileService;
import club.boyuan.official.domain.profile.dto.CandidateProfileDetail;
import club.boyuan.official.domain.profile.dto.CandidateProfileListRow;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/** 候选档案（个人主页）：管理员/面试官查看候选人跨周期聚合数据。 */
@RestController
@RequestMapping("/api/admin/profiles")
@AllArgsConstructor
public class ProfileController {

    private final IProfileService profileService;
    private final club.boyuan.official.persistence.mapper.EvaluationScopeMapper scope;

    /** 候选档案列表：有面试安排的候选人，按最近面试时间排序。跨全部周期。 */
    @GetMapping
    @PreAuthorize("hasAuthority('resume:view') or hasAuthority('evaluation:view')")
    public ResponseEntity<ResponseMessage<List<CandidateProfileListRow>>> list() {
        requireGlobal();
        return ResponseEntity.ok(
                new ResponseMessage<>(200, "候选档案列表获取成功", profileService.listCandidates()));
    }

    /**
     * 候选档案详情：某用户全部周期聚合（面试安排/评测成绩/获奖/简历）。
     * 面试官(interview:evaluate)在打分工作台据此读获奖/成绩；不授予 resume:view 全量简历库。
     * 低权限视图（仅 interview:evaluate，无 resume:view/evaluation:view）不返回 PII/简历/面试安排，
     * 只回 userId/name/username/awards/submissions —— 保住"面试官不能读个人敏感信息"边界。
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('resume:view','evaluation:view')")
    public ResponseEntity<ResponseMessage<CandidateProfileDetail>> detail(@PathVariable Integer userId) {
        requireGlobal();
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(ResponseMessage.success(profileService.getProfile(userId)));
    }

    @GetMapping("/{userId}/cycles/{cycleId}/resumes/{resumeId}")
    @PreAuthorize("hasAnyAuthority('resume:view','evaluation:view','interview:evaluate')")
    public ResponseEntity<ResponseMessage<CandidateProfileDetail>> boardDetail(@PathVariable Integer userId,
            @PathVariable Integer cycleId, @PathVariable Integer resumeId) {
        if (canReadSensitiveFields()) return detail(userId);
        var viewer = scope.viewerId(club.boyuan.official.common.utils.SecurityUtil.getCurrentUsername());
        if (viewer == null || !scope.candidate(viewer, resumeId, cycleId, null, userId, false)) {
            throw new org.springframework.security.access.AccessDeniedException("无权查看该简历的候选档案");
        }
        var full = profileService.getProfile(userId);
        var result = new CandidateProfileDetail();
        result.setUserId(userId);
        result.setName(full.getName());
        result.setUsername(full.getUsername());
        result.setResumes(List.of());
        result.setInterviews(List.of());
        result.setAwards(List.of());
        result.setSubmissions(full.getSubmissions() == null ? List.of() : full.getSubmissions().stream()
                .filter(s -> cycleId.equals(s.getCycleId())).toList());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ResponseMessage.success(result));
    }

    /** 是否具备读取个人敏感信息（PII/简历/面试安排）的权限：
         *  仅当持有 resume:view 或 evaluation:view；纯 interview:evaluate 面试官无此能力。 */
    private boolean canReadSensitiveFields() {
        Integer viewer = scope.viewerId(club.boyuan.official.common.utils.SecurityUtil.getCurrentUsername());
        return viewer != null && scope.materialGrants(viewer).stream()
                .anyMatch(p -> "resume:view".equals(p) || "evaluation:view".equals(p));
    }

    private void requireGlobal() {
        if (!canReadSensitiveFields()) throw new org.springframework.security.access.AccessDeniedException("没有查看候选档案权限");
    }
}
