package club.boyuan.official.domain.user.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.domain.user.service.MemberClaimService;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.persistence.entity.MemberClaim;
import club.boyuan.official.persistence.entity.User;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 老社员认领。
 *
 * 学生侧：注册后若本人是往届社员，提交一份认领申请并查看审批进度。
 * 管理侧：列表 + 通过/驳回；通过时把账号置为社员并归部门。
 */
@RestController
@RequestMapping("/api/member-claims")
@Slf4j
@RequiredArgsConstructor
public class MemberClaimController {

    private final MemberClaimService memberClaimService;
    private final IUserService userService;

    private User currentUser() {
        User user = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        return user;
    }

    // ── 学生侧 ──

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<MemberClaim>> submit(@RequestBody MemberClaim form) {
        User me = currentUser();
        return ResponseEntity.ok(ResponseMessage.success(memberClaimService.submit(me.getUserId(), form)));
    }

    /** 我的认领状态。返回 null 表示从未申请过——前端据此决定是否显示入口。 */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Map<String, Object>>> my() {
        User me = currentUser();
        MemberClaim latest = memberClaimService.myLatest(me.getUserId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("isMember", Boolean.TRUE.equals(me.getIsMember()));
        out.put("claim", latest);
        return ResponseEntity.ok(ResponseMessage.success(out));
    }

    // ── 管理侧 ──

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<ResponseMessage<IPage<MemberClaim>>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ResponseMessage.success(
                memberClaimService.page(status, keyword, page, size)));
    }

    /** 待审数量，给管理端菜单角标用 */
    @GetMapping("/pending-count")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<ResponseMessage<Map<String, Long>>> pendingCount() {
        return ResponseEntity.ok(ResponseMessage.success(
                Map.of("pending", memberClaimService.pendingCount())));
    }

    @PostMapping("/{claimId}/approve")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<ResponseMessage<MemberClaim>> approve(
            @PathVariable Integer claimId,
            @RequestBody(required = false) Map<String, Object> body) {
        User me = currentUser();
        Integer deptId = body == null || body.get("deptId") == null
                ? null : Integer.valueOf(String.valueOf(body.get("deptId")));
        String note = body == null || body.get("note") == null ? null : String.valueOf(body.get("note"));
        log.info("管理员{}通过老社员认领 claimId={}, deptId={}", me.getUsername(), claimId, deptId);
        return ResponseEntity.ok(ResponseMessage.success(
                memberClaimService.approve(claimId, me.getUserId(), deptId, note)));
    }

    @PostMapping("/{claimId}/reject")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<ResponseMessage<MemberClaim>> reject(
            @PathVariable Integer claimId,
            @RequestBody(required = false) Map<String, Object> body) {
        User me = currentUser();
        String note = body == null || body.get("note") == null ? null : String.valueOf(body.get("note"));
        log.info("管理员{}驳回老社员认领 claimId={}", me.getUsername(), claimId);
        return ResponseEntity.ok(ResponseMessage.success(
                memberClaimService.reject(claimId, me.getUserId(), note)));
    }
}
