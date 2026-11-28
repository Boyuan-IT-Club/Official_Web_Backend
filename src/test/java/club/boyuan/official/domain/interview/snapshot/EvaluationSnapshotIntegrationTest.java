package club.boyuan.official.domain.interview.snapshot;

import static club.boyuan.official.domain.interview.snapshot.SnapshotRequest.JSON;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import club.boyuan.official.common.utils.JwtTokenUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "evaluation.snapshot-service-token=fixture-r6",
            "evaluation.http-writes-enabled=true"
        })
@AutoConfigureMockMvc
class EvaluationSnapshotIntegrationTest {
    private static final int CYCLE = 19931006, ROW = 19931007, A = 19931008, B = 19931009;
    private static final String PATH = "/api/internal/evaluation/boards/" + CYCLE;
    private static final String DOC = "eval-board:" + CYCLE;
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenUtil jwt;
    private ObjectNode fixtures;
    private String tokenA, tokenB;

    @Test
    void preservesLegacyBaseAndAttributionAndDurableReceipts() throws Exception {
        fixtures =
                (ObjectNode)
                        JSON.readTree(
                                getClass()
                                        .getResourceAsStream(
                                                "/contracts/r6/evaluation-snapshots.json"));
        try {
            seed();
            tokenA = jwt.generateToken("evaluation-r6-a", A, List.of());
            tokenB = jwt.generateToken("evaluation-r6-b", B, List.of());
            JsonNode baseline = data(get(PATH + "/snapshot"), tokenA, 200);
            assertEquals(0, baseline.path("storedRevision").intValue());
            assertEquals(7, baseline.path("baseRevision").intValue());
            assertEquals(fixtures.get("old"), baseline.get("authors"));
            assertEquals(
                    baseline.get("scheduleIds"),
                    data(get(PATH + "/snapshot"), tokenB, 200).get("scheduleIds"));
            assertEquals(
                    baseline.get("dimensionIds"),
                    data(get(PATH + "/snapshot"), tokenB, 200).get("dimensionIds"));
            assertEquals(
                    401,
                    http.perform(get(PATH + "/snapshot").header("X-Evaluation-User-Token", tokenA))
                            .andReturn()
                            .getResponse()
                            .getStatus());
            data(get(PATH + "/snapshot"), jwt.generateToken("evaluation-r6-a", B, List.of()), 403);
            data(get(PATH + "/snapshot"), null, 401);
            ObjectNode first = commit("one", 0), second = commit("two", 8);
            var forged = first.deepCopy();
            forged.with("authors").with("rows").with(Integer.toString(ROW)).put("lastEditedBy", B);
            postCommit(forged, tokenA, 400);
            var outOfScope = first.deepCopy();
            outOfScope
                    .withArray("changedCells")
                    .removeAll()
                    .addArray()
                    .add(Integer.toString(ROW))
                    .add("dim:1599700");
            postCommit(outOfScope, tokenA, 403);

            var pool = Executors.newFixedThreadPool(2);
            var start = new CyclicBarrier(2);
            try {
                Callable<JsonNode> attempt =
                        () -> {
                            start.await(5, TimeUnit.SECONDS);
                            return postCommit(first, tokenA, 200);
                        };
                var one = pool.submit(attempt);
                var retry = pool.submit(attempt);
                var receipt = one.get(15, TimeUnit.SECONDS);
                assertEquals(receipt, retry.get(15, TimeUnit.SECONDS));
                assertEquals(
                        HexFormat.of()
                                .formatHex(
                                        SnapshotRequest.hash(
                                                Base64.getDecoder()
                                                        .decode(first.get("state").textValue()))),
                        receipt.get("digest").textValue());
            } finally {
                pool.shutdownNow();
            }

            postCommit(commit("race", 0), tokenB, 409);
            assertEquals(
                    1,
                    db.queryForObject(
                            "SELECT COUNT(*) FROM collab_audit WHERE doc_name=? AND rejected=1",
                            Integer.class,
                            DOC));
            var stillFirst = data(get(PATH + "/snapshot"), tokenA, 200);
            assertEquals(first.get("state"), stillFirst.get("state"));
            assertEquals(first.get("authors"), stillFirst.get("authors"));
            assertEquals(8L, stillFirst.get("storedRevision").longValue());
            postCommit(second, tokenB, 200);
            assertEquals(8L, postCommit(first, tokenA, 200).get("revision").longValue());
            data(get(PATH + "/commits/" + first.path("operationId").asText()), tokenB, 404);
            data(get(PATH + "/commits/" + first.path("operationId").asText()), tokenA, 200);
            var changed = first.deepCopy();
            changed.withArray("changedCells").addArray().add(Integer.toString(ROW)).add("status");
            postCommit(changed, tokenA, 409);
            assertEquals(
                    2,
                    db.queryForObject(
                            "SELECT COUNT(*) FROM evaluation_commit_receipt WHERE doc_name=?",
                            Integer.class,
                            DOC));
            assertEquals(
                    second.get("state"), data(get(PATH + "/snapshot"), tokenA, 200).get("state"));
            JsonNode pending =
                    data(get("/api/internal/evaluation/materialization/pending"), null, 200);
            assertTrue(
                    java.util.stream.StreamSupport.stream(pending.get("items").spliterator(), false)
                            .anyMatch(
                                    row ->
                                            row.path("docName").asText().equals(DOC)
                                                    && row.path("storedRevision").longValue()
                                                            == 9));
            // A SQL-era commit can lack an HTTP receipt. Same bytes plus equivalent authors
            // may be recovered by their real editor, without advancing or replacing the stored
            // state.
            ObjectNode legacyRetry =
                    second.deepCopy().put("operationId", UUID.randomUUID().toString());
            postCommit(legacyRetry, tokenB, 200);
            assertEquals(
                    9L,
                    data(get(PATH + "/snapshot"), tokenA, 200).path("storedRevision").longValue());
            db.update("UPDATE collab_doc SET locked=1 WHERE doc_name=?", DOC);
            postCommit(commit("two", 8), tokenB, 403);
            db.update("DELETE FROM user_role WHERE user_id=?", B);
            data(get(PATH + "/snapshot"), tokenB, 403);
            jwt.revokeToken(tokenA);
            data(get(PATH + "/snapshot"), tokenA, 401);
        } finally {
            db.update("DELETE FROM collab_audit WHERE doc_name=?", DOC);
            db.update("DELETE FROM collab_doc WHERE doc_name=? AND cycle_id=?", DOC, CYCLE);
            db.update("DELETE FROM interview_evaluation WHERE schedule_id=?", ROW);
            db.update("DELETE FROM interview_schedule WHERE schedule_id=?", ROW);
            db.update("DELETE FROM resume WHERE resume_id=?", ROW);
            db.update("DELETE FROM recruitment_cycle WHERE cycle_id=?", CYCLE);
            db.update("DELETE FROM `user` WHERE user_id IN (?,?)", A, B);
        }
    }

    private ObjectNode commit(String fixture, long expected) {
        return ((ObjectNode) fixtures.get(fixture).deepCopy())
                .put("operationId", UUID.randomUUID().toString())
                .put("expectedStoredRevision", expected);
    }

    private JsonNode postCommit(ObjectNode commit, String token, int status) throws Exception {
        return data(
                post(PATH + "/commits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commit.toString()),
                token,
                status);
    }

    private JsonNode data(MockHttpServletRequestBuilder req, String token, int status)
            throws Exception {
        req.header("X-Service-Token", "fixture-r6");
        if (token != null) req.header("X-Evaluation-User-Token", token);
        var response = http.perform(req).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        if (response.getContentAsByteArray().length == 0) return null;
        return JSON.readTree(response.getContentAsByteArray()).get("data");
    }

    private void seed() {
        db.update(
                "INSERT INTO `user`(user_id,username,password,name,email) VALUES"
                    + " (?,?,?,?,?),(?,?,?,?,?)",
                A,
                "evaluation-r6-a",
                "fixture",
                "A",
                "evaluation-r6-a@example.invalid",
                B,
                "evaluation-r6-b",
                "fixture",
                "B",
                "evaluation-r6-b@example.invalid");
        db.update(
                "INSERT INTO user_role(user_id,role_id) SELECT u.user_id,r.role_id FROM `user` u"
                    + " JOIN role r ON r.role_code='INTERVIEWER' WHERE u.user_id IN (?,?)",
                A,
                B);
        db.update(
                "INSERT INTO"
                    + " recruitment_cycle(cycle_id,cycle_name,start_date,end_date,academic_year,status)"
                    + " VALUES (?,'R6 Evaluation Fixture','2099-01-01','2099-01-31','2099-2100',1)",
                CYCLE);
        db.update(
                "INSERT INTO resume(resume_id,user_id,cycle_id,status) VALUES (?,?,?,3)",
                ROW,
                B,
                CYCLE);
        db.update(
                "INSERT INTO interview_schedule(schedule_id,resume_id,user_id,cycle_id,status)"
                    + " VALUES (?,?,?,?,1)",
                ROW,
                ROW,
                B,
                CYCLE);
        db.update("INSERT INTO collab_doc(doc_name,cycle_id) VALUES(?,?)", DOC, CYCLE);
        db.update(
                "INSERT INTO"
                    + " interview_evaluation(schedule_id,cycle_id,resume_id,contributors,last_edited_by,status,version)"
                    + " VALUES (?,?,?,JSON_ARRAY(?),?,1,7)",
                ROW,
                CYCLE,
                ROW,
                B,
                B);
    }
}
