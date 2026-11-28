package club.boyuan.official.domain.interview.snapshot;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

record SnapshotRequest(
        UUID operationId,
        long expected,
        long next,
        byte[] state,
        ObjectNode authors,
        SortedMap<Integer, SortedSet<String>> changes,
        byte[] stateHash,
        byte[] requestHash) {
    static final int MAX_BODY = 16 * 1024 * 1024;
    static final int MAX_STATE = 8 * 1024 * 1024;
    static final long MAX_SAFE = 9_007_199_254_740_991L;
    static final Pattern DIMENSION = Pattern.compile("dim:([1-9][0-9]{0,9})(:note)?");
    static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    static SnapshotRequest parse(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) throw SnapshotFailure.tooLarge();
        JsonNode body;
        try {
            body = JSON.readTree(bytes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            throw SnapshotFailure.invalid();
        }
        if (body == null || !body.isObject() || body.size() != 6) throw SnapshotFailure.invalid();
        UUID operation = uuid(body.path("operationId").asText(""));
        long expected = version(body.path("expectedStoredRevision")),
                next = version(body.path("nextRevision"));
        if (next <= expected || !body.path("state").isTextual()) throw SnapshotFailure.invalid();
        String encoded = body.get("state").textValue();
        if (encoded.length() > 4 * ((MAX_STATE + 2) / 3)) throw SnapshotFailure.tooLarge();
        byte[] state;
        try {
            state = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException malformed) {
            throw SnapshotFailure.invalid();
        }
        if (state.length > MAX_STATE) throw SnapshotFailure.tooLarge();
        if (state.length == 0) throw SnapshotFailure.invalid();
        if (!(body.get("authors") instanceof ObjectNode authors)
                || authors.size() != 1
                || !authors.path("rows").isObject()) {
            throw SnapshotFailure.invalid();
        }
        JsonNode cells = body.path("changedCells");
        if (!cells.isArray() || cells.isEmpty()) throw SnapshotFailure.invalid();
        SortedMap<Integer, SortedSet<String>> changes = new TreeMap<>();
        for (JsonNode pair : cells) {
            if (!pair.isArray()
                    || pair.size() != 2
                    || !pair.get(0).isTextual()
                    || !pair.get(1).isTextual()) throw SnapshotFailure.invalid();
            int row = positiveId(pair.get(0).textValue());
            String key = pair.get(1).textValue();
            if (!Set.of("comment", "recommendation", "status").contains(key)
                    && !DIMENSION.matcher(key).matches()) {
                throw SnapshotFailure.invalid();
            }
            changes.computeIfAbsent(row, ignored -> new TreeSet<>()).add(key);
        }
        byte[] stateHash = hash(state);
        ObjectNode fingerprint =
                JSON.createObjectNode()
                        .put("expected", expected)
                        .put("next", next)
                        .put("state", HexFormat.of().formatHex(stateHash));
        fingerprint.set("authors", canonical(authors, 0));
        fingerprint.set("changes", JSON.valueToTree(changes));
        return new SnapshotRequest(
                operation,
                expected,
                next,
                state,
                authors,
                changes,
                stateHash,
                hash(JSON.writeValueAsBytes(fingerprint)));
    }

    static UUID uuid(String text) {
        try {
            UUID id = UUID.fromString(text);
            if (!id.toString().equalsIgnoreCase(text)) throw SnapshotFailure.invalid();
            return id;
        } catch (IllegalArgumentException error) {
            throw SnapshotFailure.invalid();
        }
    }

    static int positiveId(String text) {
        try {
            int value = Integer.parseInt(text);
            if (value < 1 || !Integer.toString(value).equals(text)) throw SnapshotFailure.invalid();
            return value;
        } catch (NumberFormatException error) {
            throw SnapshotFailure.invalid();
        }
    }

    private static long version(JsonNode value) {
        if (!value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 0
                || value.longValue() > MAX_SAFE) {
            throw SnapshotFailure.invalid();
        }
        return value.longValue();
    }

    static byte[] hash(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    static JsonNode canonical(JsonNode node, int depth) {
        if (depth > 32) throw SnapshotFailure.invalid();
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeSet<String> keys = new TreeSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> result.set(key, canonical(node.get(key), depth + 1)));
            return result;
        }
        if (node.isArray()) {
            var result = JSON.createArrayNode();
            node.forEach(item -> result.add(canonical(item, depth + 1)));
            return result;
        }
        if (node.isNumber())
            return JSON.getNodeFactory().numberNode(node.decimalValue().stripTrailingZeros());
        return node;
    }
}
