package club.boyuan.official.domain.interview.snapshot;

import static club.boyuan.official.domain.interview.snapshot.SnapshotRequest.JSON;

import club.boyuan.official.persistence.repository.EvaluationSnapshotStore;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.*;

@Service
class EvaluationSnapshots {
    record Snapshot(
            String docName,
            int cycleId,
            byte[] state,
            boolean locked,
            long storedRevision,
            long baseRevision,
            ObjectNode authors,
            List<Integer> scheduleIds,
            List<Integer> dimensionIds) {}

    record Receipt(UUID operationId, long revision, String digest) {}

    record Result(Receipt receipt, boolean conflict) {}

    record Pending(String docName, int cycleId, long storedRevision, ObjectNode authors) {}

    record Page(List<Pending> items, String nextCursor) {}

    private final EvaluationSnapshotStore store;
    private final boolean writes;

    EvaluationSnapshots(
            EvaluationSnapshotStore store,
            @Value("${evaluation.http-writes-enabled:false}") boolean writes) {
        this.store = store;
        this.writes = writes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot(int cycle, EvaluationActor.Identity actor) {
        requireAccess(actor, false);
        var board = board(cycle, false);
        var base = store.baseline(board);
        return new Snapshot(
                board.name(),
                cycle,
                board.state(),
                board.locked(),
                board.revision(),
                base.revision(),
                base.authors(),
                store.roster(cycle, false),
                List.copyOf(store.dimensions(cycle, false).keySet()));
    }

    void requireWriter() {
        if (!writes) throw new SnapshotFailure(HttpStatus.SERVICE_UNAVAILABLE, "评价快照尚未切换为 HTTP 写入");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result commit(int cycle, EvaluationActor.Identity actor, SnapshotRequest request) {
        requireWriter();
        requireAccess(actor, true);
        var board = board(cycle, true);
        var previous = store.receipt(board.name(), request.operationId());
        if (previous != null) {
            if (previous.actor() != actor.id()) throw SnapshotFailure.denied();
            if (MessageDigest.isEqual(previous.fingerprint(), request.requestHash())) {
                return new Result(
                        receipt(request.operationId(), previous.revision(), previous.stateHash()),
                        false);
            }
            return conflict(board, request, actor.id());
        }
        if (board.locked()) throw SnapshotFailure.denied();
        if (board.revision() != request.expected()) {
            // A legacy SQL writer can have committed this exact payload without an operation
            // receipt.
            if (board.revision() == request.next()
                    && Arrays.equals(board.state(), request.state())
                    && board.authors() != null
                    && SnapshotRequest.canonical(board.authors(), 0)
                            .equals(SnapshotRequest.canonical(request.authors(), 0))
                    && SnapshotAuthors.alreadyCommittedBy(board.authors(), request, actor.id())) {
                remember(board, request, actor.id());
                return new Result(
                        receipt(request.operationId(), request.next(), request.stateHash()), false);
            }
            return conflict(board, request, actor.id());
        }
        var base = store.baseline(board);
        if (request.next() != base.revision() + 1) return conflict(board, request, actor.id());
        SnapshotAuthors.verify(
                base.authors(),
                request,
                actor.id(),
                new HashSet<>(store.roster(cycle, true)),
                store.dimensions(cycle, true));
        store.saveState(board.name(), request.state(), request.authors(), request.next());
        remember(board, request, actor.id());
        return new Result(
                receipt(request.operationId(), request.next(), request.stateHash()), false);
    }

    private void requireAccess(EvaluationActor.Identity actor, boolean lock) {
        if (!store.hasGrant(actor.id(), actor.username(), lock)) throw SnapshotFailure.denied();
    }

    private EvaluationSnapshotStore.Board board(int cycle, boolean lock) {
        var board = store.board(cycle, lock);
        if (board == null) throw new SnapshotFailure(HttpStatus.NOT_FOUND, "评价板不存在");
        return board;
    }

    private void remember(EvaluationSnapshotStore.Board board, SnapshotRequest request, int actor) {
        store.remember(
                board.name(),
                request.operationId(),
                actor,
                request.expected(),
                request.next(),
                request.requestHash(),
                request.stateHash());
    }

    private Result conflict(
            EvaluationSnapshotStore.Board board, SnapshotRequest request, int actor) {
        ObjectNode evidence =
                JSON.createObjectNode()
                        .put("operationId", request.operationId().toString())
                        .put("expectedStoredRevision", request.expected())
                        .put("revision", request.next())
                        .put("committedRevision", board.revision())
                        .put("rejectedHash", HexFormat.of().formatHex(request.stateHash()))
                        .put(
                                "committedHash",
                                HexFormat.of()
                                        .formatHex(
                                                SnapshotRequest.hash(
                                                        board.state() == null
                                                                ? new byte[0]
                                                                : board.state())))
                        .put("rejectedState", Base64.getEncoder().encodeToString(request.state()));
        evidence.set("rows", JSON.valueToTree(request.changes().keySet()));
        evidence.set("rejectedAuthors", request.authors());
        if (board.authors() != null)
            evidence.put(
                    "committedAuthorsHash",
                    HexFormat.of()
                            .formatHex(
                                    SnapshotRequest.hash(
                                            SnapshotRequest.canonical(board.authors(), 0)
                                                    .toString()
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8))));
        store.audit(board.name(), actor, evidence);
        // Return normally so Spring commits the rejected snapshot evidence before the controller
        // emits 409.
        return new Result(null, true);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Receipt lookup(int cycle, EvaluationActor.Identity actor, UUID operation) {
        requireAccess(actor, false);
        var row = store.receipt("eval-board:" + cycle, operation);
        if (row == null || row.actor() != actor.id())
            throw new SnapshotFailure(HttpStatus.NOT_FOUND, "没有此操作回执");
        return receipt(operation, row.revision(), row.stateHash());
    }

    private static Receipt receipt(UUID operation, long revision, byte[] digest) {
        return new Receipt(operation, revision, HexFormat.of().formatHex(digest));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page pending(String cursor, int limit) {
        if (limit < 1
                || limit > 20
                || (!cursor.isEmpty() && !cursor.matches("eval-board:[1-9][0-9]{0,9}")))
            throw SnapshotFailure.invalid();
        var docs =
                store.pending(cursor, limit + 1).stream()
                        .map(
                                row ->
                                        new Pending(
                                                row.docName(),
                                                row.cycleId(),
                                                row.storedRevision(),
                                                row.authors()))
                        .toList();
        if (docs.size() <= limit) return new Page(docs, null);
        return new Page(docs.subList(0, limit), docs.get(limit - 1).docName());
    }
}
