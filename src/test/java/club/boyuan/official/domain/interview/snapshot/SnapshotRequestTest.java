package club.boyuan.official.domain.interview.snapshot;

import static club.boyuan.official.domain.interview.snapshot.SnapshotRequest.JSON;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.UUID;

class SnapshotRequestTest {
    private ObjectNode request() throws Exception {
        var fixtures =
                JSON.readTree(
                        getClass().getResourceAsStream("/contracts/r6/evaluation-snapshots.json"));
        return ((ObjectNode) fixtures.get("one"))
                .deepCopy()
                .put("operationId", UUID.randomUUID().toString())
                .put("expectedStoredRevision", 0);
    }

    private static SnapshotRequest parse(ObjectNode source) throws Exception {
        return SnapshotRequest.parse(new ByteArrayInputStream(JSON.writeValueAsBytes(source)));
    }

    @Test
    void semanticRetryFingerprintsWholeCommitWhilePublicDigestCoversStateBytes() throws Exception {
        var original = request();
        var reordered = original.deepCopy();
        reordered.withArray("changedCells").add(original.get("changedCells").get(0));
        var rows = reordered.with("authors").remove("rows");
        reordered.with("authors").set("rows", rows);
        assertArrayEquals(parse(original).requestHash(), parse(reordered).requestHash());
        reordered.with("authors").with("rows").with("19931007").put("lastEditedBy", 19931009);
        assertFalse(
                java.util.Arrays.equals(
                        parse(original).requestHash(), parse(reordered).requestHash()));
        assertArrayEquals(parse(original).stateHash(), parse(reordered).stateHash());
    }

    @Test
    void enforcesDecodedSizeAndStreamingBodySize() throws Exception {
        var original = request();
        original.put(
                "state",
                Base64.getEncoder().encodeToString(new byte[SnapshotRequest.MAX_STATE + 1]));
        assertEquals(
                HttpStatus.PAYLOAD_TOO_LARGE,
                assertThrows(SnapshotFailure.class, () -> parse(original)).status);
        assertEquals(
                HttpStatus.PAYLOAD_TOO_LARGE,
                assertThrows(
                                SnapshotFailure.class,
                                () ->
                                        SnapshotRequest.parse(
                                                new ByteArrayInputStream(
                                                        new byte[SnapshotRequest.MAX_BODY + 1])))
                        .status);
    }

    @Test
    void disabledWriterRefusesCommitsBeforeReadingOrMutatingStorage() {
        var service = new EvaluationSnapshots(null, false);
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(SnapshotFailure.class, service::requireWriter).status);
    }
}
