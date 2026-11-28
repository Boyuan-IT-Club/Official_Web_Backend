package club.boyuan.official.domain.workbench;

import club.boyuan.official.common.dto.ResponseMessage;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 服务目录凭据只读。成员写入不走这里。 */
@RestController
@RequestMapping("/api/internal/workbench")
public class WorkbenchDirectoryController {
    private final WorkbenchMembershipService membership;

    public WorkbenchDirectoryController(WorkbenchMembershipService membership) {
        this.membership = membership;
    }

    @PostMapping("/member-names")
    @PreAuthorize("hasAuthority('WORKBENCH_DIRECTORY')")
    public ResponseMessage<List<WorkbenchMembershipService.Name>> names(@RequestBody List<Long> ids) {
        return ResponseMessage.success(membership.names(ids));
    }

    @GetMapping("/dev-members")
    @PreAuthorize("hasAuthority('WORKBENCH_DIRECTORY')")
    public ResponseMessage<WorkbenchMembershipService.SnapshotPage> list(
        @RequestParam(defaultValue = "0") String cursor,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return ResponseMessage.success(membership.reconcile(cursor, limit));
    }

    @GetMapping("/dev-members/{userId}")
    @PreAuthorize("hasAuthority('WORKBENCH_DIRECTORY')")
    public ResponseMessage<WorkbenchMembershipService.Snapshot> one(@PathVariable String userId) {
        return ResponseMessage.success(membership.one(parse(userId)));
    }

    private static int parse(String userId) {
        if (userId == null || !userId.matches("[1-9]\\d{0,9}")) {
            throw new WorkbenchMembershipException(HttpStatus.NOT_FOUND, "目标账号不存在");
        }
        long value = Long.parseLong(userId);
        if (value > Integer.MAX_VALUE) {
            throw new WorkbenchMembershipException(HttpStatus.NOT_FOUND, "目标账号不存在");
        }
        return (int) value;
    }
}
