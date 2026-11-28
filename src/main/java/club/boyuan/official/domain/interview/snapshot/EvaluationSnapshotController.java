package club.boyuan.official.domain.interview.snapshot;

import club.boyuan.official.common.dto.ResponseMessage;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/internal/evaluation")
@PreAuthorize("hasAuthority('EVALUATION_SNAPSHOTS') and principal instanceof T(club.boyuan.official.infra.filter.ServiceTokenAuthenticationFilter$StoragePrincipal)")
public class EvaluationSnapshotController {
    private static final String USER = "X-Evaluation-User-Token";
    private final EvaluationSnapshots snapshots;
    private final EvaluationActor actors;

    EvaluationSnapshotController(EvaluationSnapshots snapshots, EvaluationActor actors) {
        this.snapshots = snapshots;
        this.actors = actors;
    }

    @GetMapping("/boards/{cycleId}/snapshot")
    public ResponseEntity<?> snapshot(
            @PathVariable int cycleId, @RequestHeader(name = USER, required = false) String token) {
        return ok(snapshots.snapshot(cycleId, actors.verify(token)));
    }

    @PostMapping(value = "/boards/{cycleId}/commits", consumes = "application/json")
    public ResponseEntity<?> commit(
            @PathVariable int cycleId,
            @RequestHeader(name = USER, required = false) String token,
            HttpServletRequest http)
            throws IOException {
        snapshots.requireWriter();
        var actor = actors.verify(token);
        if (http.getContentLengthLong() > SnapshotRequest.MAX_BODY)
            throw SnapshotFailure.tooLarge();
        var result = snapshots.commit(cycleId, actor, SnapshotRequest.parse(http.getInputStream()));
        if (result.conflict()) return error(HttpStatus.CONFLICT, "评价快照已冲突，已保留首次提交");
        return ok(result.receipt());
    }

    @GetMapping("/boards/{cycleId}/commits/{operationId}")
    public ResponseEntity<?> receipt(
            @PathVariable int cycleId,
            @PathVariable String operationId,
            @RequestHeader(name = USER, required = false) String token) {
        return ok(
                snapshots.lookup(cycleId, actors.verify(token), SnapshotRequest.uuid(operationId)));
    }

    @GetMapping("/materialization/pending")
    public ResponseEntity<?> pending(
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ok(snapshots.pending(cursor, limit));
    }

    private static ResponseEntity<?> ok(Object value) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseMessage.success(value));
    }

    @ExceptionHandler(SnapshotFailure.class)
    public ResponseEntity<?> failure(SnapshotFailure failure) {
        return error(failure.status, failure.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<?> unavailable(RuntimeException error) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("评价快照数据库或身份缓存暂不可用", error);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "评价状态暂不可知，请按操作号核对");
    }

    private static ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(ResponseMessage.error(status.value(), message));
    }
}
