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

    /** 候选档案列表：有面试安排的候选人，按最近面试时间排序。跨全部周期。 */
    @GetMapping
    @PreAuthorize("hasAuthority('resume:view') or hasAuthority('evaluation:view')")
    public ResponseEntity<ResponseMessage<List<CandidateProfileListRow>>> list() {
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
    @PreAuthorize("hasAuthority('resume:view') or hasAuthority('evaluation:view') or hasAuthority('interview:evaluate')")
    public ResponseEntity<ResponseMessage<CandidateProfileDetail>> detail(@PathVariable Integer userId) {
        CandidateProfileDetail detail = profileService.getProfile(userId);
        if (!canReadSensitiveFields()) {
            // 仅 interview:evaluate：裁剪敏感字段，保留评委打分所需的获奖/成绩
            detail.setEmail(null);
            detail.setPhone(null);
            detail.setGithub(null);
            detail.setMajor(null);
            detail.setDeptName(null);
            detail.setInterviews(Collections.emptyList());
            detail.setResumes(Collections.emptyList());
        }
        return ResponseEntity.ok(
                new ResponseMessage<>(200, "候选档案详情获取成功", detail));
    }

    /** 是否具备读取个人敏感信息（PII/简历/面试安排）的权限：
         *  仅当持有 resume:view 或 evaluation:view；纯 interview:evaluate 面试官无此能力。 */
    private boolean canReadSensitiveFields() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "resume:view".equals(a.getAuthority())
                        || "evaluation:view".equals(a.getAuthority()));
    }
}
