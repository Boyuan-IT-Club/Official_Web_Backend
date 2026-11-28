package club.boyuan.official.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class EvaluationSnapshotStore {
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public record Board(
            String name,
            int cycle,
            byte[] state,
            boolean locked,
            long revision,
            ObjectNode authors) {}

    public record Baseline(long revision, ObjectNode authors) {}

    public record Receipt(int actor, long revision, byte[] fingerprint, byte[] stateHash) {}

    public record Pending(String docName, int cycleId, long storedRevision, ObjectNode authors) {}

    private final JdbcTemplate db;

    EvaluationSnapshotStore(JdbcTemplate db) {
        this.db = db;
    }

    public Board board(int cycle, boolean lock) {
        return db.query(
                "SELECT doc_name,cycle_id,state,locked,snapshot_revision,author_state FROM"
                        + " collab_doc WHERE doc_name=? AND cycle_id=?"
                        + (lock ? " FOR UPDATE" : ""),
                rs -> {
                    if (!rs.next()) return null;
                    return new Board(
                            rs.getString(1),
                            rs.getInt(2),
                            rs.getBytes(3),
                            rs.getBoolean(4),
                            rs.getLong(5),
                            (ObjectNode) json(rs.getString(6), null));
                },
                "eval-board:" + cycle,
                cycle);
    }

    public boolean hasGrant(int id, String username, boolean lock) {
        // Lock live grant edges for the duration of the write, including account status and the
        // role itself.
        var grants =
                db.queryForList(
                        """
                        SELECT p.permission_code FROM `user` u JOIN user_role ur ON u.user_id=ur.user_id
                        JOIN role r ON ur.role_id=r.role_id AND r.status=1 JOIN role_permission rp ON rp.role_id=r.role_id
                        JOIN permission p ON p.permission_id=rp.permission_id
                        WHERE u.user_id=? AND u.username=? AND u.status=1 AND u.is_deleted=0
                          AND p.permission_code IN ('interview:evaluate','interview:board:manage','resume:audit')
                        """
                                + (lock ? " FOR SHARE" : ""),
                        String.class,
                        id,
                        username);
        return !grants.isEmpty();
    }

    public List<Integer> roster(int cycle, boolean lock) {
        return db.queryForList(
                "SELECT schedule_id FROM evaluation_board_candidate WHERE cycle_id=? ORDER BY"
                        + " schedule_id"
                        + (lock ? " FOR SHARE" : ""),
                Integer.class,
                cycle);
    }

    public Map<Integer, Integer> dimensions(int cycle, boolean lock) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        db.query(
                "SELECT dimension_id,max_score FROM evaluation_dimension WHERE cycle_id=? ORDER BY"
                        + " dimension_id"
                        + (lock ? " FOR SHARE" : ""),
                rs -> {
                    result.put(rs.getInt(1), rs.getInt(2));
                },
                cycle);
        return result;
    }

    public Baseline baseline(Board board) {
        if (board.revision() > 0) return new Baseline(board.revision(), board.authors());
        ObjectNode authors = JSON.createObjectNode(), rows = authors.putObject("rows");
        return db.query(
                """
                SELECT schedule_id,contributors,dimension_writers,last_edited_by,submitted_by,
                       DATE_FORMAT(submitted_at,'%Y-%m-%dT%H:%i:%s') submitted_at,
                       scores,dimension_notes,comment,recommendation,status,version
                FROM interview_evaluation WHERE cycle_id=?
                """,
                rs -> {
                    long base = 0;
                    while (rs.next()) {
                        int id = rs.getInt("schedule_id");
                        ObjectNode row = rows.putObject(Integer.toString(id));
                        ObjectNode item = row.putObject("materialized");
                        item.put("scheduleId", id)
                                .put("status", rs.getInt("status"))
                                .put("version", rs.getLong("version"));
                        for (var field :
                                Map.of(
                                                "contributors",
                                                "contributors",
                                                "dimensionWriters",
                                                "dimension_writers")
                                        .entrySet()) {
                            var fallback =
                                    field.getKey().equals("contributors")
                                            ? JSON.createArrayNode()
                                            : JSON.createObjectNode();
                            JsonNode value = json(rs.getString(field.getValue()), fallback);
                            item.set(field.getKey(), value);
                            row.set(field.getKey(), value);
                        }
                        copyScalar(rs, row, item, "lastEditedBy", "last_edited_by");
                        copyScalar(rs, row, item, "submittedBy", "submitted_by");
                        copyScalar(rs, row, item, "submittedAt", "submitted_at");
                        ObjectNode cells = row.putObject("cells");
                        if (!row.get("submittedBy").isNull())
                            cells.set("status", row.get("submittedBy"));
                        item.set("scores", json(rs.getString("scores"), JSON.createObjectNode()));
                        item.set(
                                "dimensionNotes",
                                json(rs.getString("dimension_notes"), JSON.createObjectNode()));
                        item.put("comment", rs.getString("comment"));
                        item.set(
                                "recommendation", JSON.valueToTree(rs.getObject("recommendation")));
                        base = Math.max(base, rs.getLong("version"));
                    }
                    return new Baseline(base, authors);
                },
                board.cycle());
    }

    private static void copyScalar(
            ResultSet rs, ObjectNode row, ObjectNode item, String key, String column)
            throws SQLException {
        JsonNode value = JSON.valueToTree(rs.getObject(column));
        row.set(key, value);
        item.set(key, value);
    }

    public Receipt receipt(String doc, UUID operation) {
        return db.query(
                "SELECT actor_id,committed_revision,request_hash,state_hash FROM"
                        + " evaluation_commit_receipt WHERE doc_name=? AND operation_id=?",
                rs ->
                        rs.next()
                                ? new Receipt(
                                        rs.getInt(1), rs.getLong(2), rs.getBytes(3), rs.getBytes(4))
                                : null,
                doc,
                operation.toString());
    }

    public void saveState(String doc, byte[] state, ObjectNode authors, long revision) {
        db.update(
                "UPDATE collab_doc SET state=?,author_state=?,snapshot_revision=? WHERE doc_name=?",
                state,
                authors.toString(),
                revision,
                doc);
    }

    public void remember(
            String doc,
            UUID operation,
            int actor,
            long expected,
            long revision,
            byte[] fingerprint,
            byte[] stateHash) {
        db.update(
                """
INSERT INTO evaluation_commit_receipt(doc_name,operation_id,actor_id,expected_revision,committed_revision,request_hash,state_hash)
VALUES(?,?,?,?,?,?,?)
""",
                doc,
                operation.toString(),
                actor,
                expected,
                revision,
                fingerprint,
                stateHash);
    }

    public void audit(String doc, int actor, ObjectNode evidence) {
        db.update(
                "INSERT INTO collab_audit(doc_name,user_id,rejected,snapshot_conflict)"
                    + " VALUES(?,?,1,?)",
                doc,
                actor,
                evidence.toString());
    }

    public List<Pending> pending(String cursor, int limit) {
        return db.query(
                """
SELECT d.doc_name,d.cycle_id,d.snapshot_revision,d.author_state FROM collab_doc d
WHERE d.doc_name>? AND d.snapshot_revision>0 AND EXISTS (
  SELECT 1 FROM JSON_TABLE(d.author_state,'$.rows.*' COLUMNS(
    edited BIGINT PATH '$.editedRevision', schedule_id INT PATH '$.materialized.scheduleId'
  )) edits JOIN interview_schedule s ON s.schedule_id=edits.schedule_id AND s.cycle_id=d.cycle_id
  LEFT JOIN interview_evaluation e ON e.schedule_id=s.schedule_id
  WHERE edits.edited>COALESCE(e.version,0))
ORDER BY d.doc_name LIMIT ?
""",
                (rs, i) ->
                        new Pending(
                                rs.getString(1),
                                rs.getInt(2),
                                rs.getLong(3),
                                (ObjectNode) json(rs.getString(4), null)),
                cursor,
                limit);
    }

    private static JsonNode json(String value, JsonNode fallback) {
        if (value == null) return fallback;
        try {
            return JSON.readTree(value);
        } catch (JsonProcessingException bad) {
            throw new IllegalStateException("已存储的评价 JSON 无效", bad);
        }
    }
}
