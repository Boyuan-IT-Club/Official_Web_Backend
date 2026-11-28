package club.boyuan.official.domain.workbench;

import club.boyuan.official.common.dto.ResponseMessage;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 工作台透传操作者 Bearer。管理写入只动研发组角色，不走需要 role:assign 的整组替换。 */
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchAccessController {
    private final WorkbenchMembershipService membership;

    public WorkbenchAccessController(WorkbenchMembershipService membership) {
        this.membership = membership;
    }

    @GetMapping("/access-context")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<WorkbenchMembershipService.AccessContext> accessContext() {
        return ResponseMessage.success(membership.accessContext());
    }

    @GetMapping("/member-candidates")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<WorkbenchMembershipService.CandidatePage> candidates(
        @RequestParam(name = "q", defaultValue = "") String q,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseMessage.success(membership.candidates(q, page, size));
    }

    @PutMapping("/dev-members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<WorkbenchMembershipService.DevMemberState> add(@PathVariable String userId) {
        return ResponseMessage.success(membership.add(parse(userId)));
    }

    @DeleteMapping("/dev-members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<WorkbenchMembershipService.DevMemberState> remove(@PathVariable String userId) {
        return ResponseMessage.success(membership.remove(parse(userId)));
    }

    private static int parse(String userId) {
        if (userId == null || !userId.matches("[1-9]\\d{0,9}")) {
            throw new WorkbenchMembershipException(HttpStatus.NOT_FOUND, "目标账号不存在");
        }
        try {
            long value = Long.parseLong(userId);
            if (value > Integer.MAX_VALUE) throw new NumberFormatException();
            return (int) value;
        } catch (NumberFormatException e) {
            throw new WorkbenchMembershipException(HttpStatus.NOT_FOUND, "目标账号不存在");
        }
    }
}
