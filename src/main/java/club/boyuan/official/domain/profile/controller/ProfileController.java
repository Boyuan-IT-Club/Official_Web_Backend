package club.boyuan.official.domain.profile.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.profile.IProfileService;
import club.boyuan.official.domain.profile.dto.CandidateProfileDetail;
import club.boyuan.official.domain.profile.dto.CandidateProfileListRow;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** 候选档案详情：某用户全部周期聚合（面试安排/评测成绩/获奖/简历）。
         *  面试官(interview:evaluate)在打分工作台据此读获奖/成绩；不授予 resume:view 全量简历库。 */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('resume:view') or hasAuthority('evaluation:view') or hasAuthority('interview:evaluate')")
    public ResponseEntity<ResponseMessage<CandidateProfileDetail>> detail(@PathVariable Integer userId) {
        return ResponseEntity.ok(
                new ResponseMessage<>(200, "候选档案详情获取成功", profileService.getProfile(userId)));
    }
}