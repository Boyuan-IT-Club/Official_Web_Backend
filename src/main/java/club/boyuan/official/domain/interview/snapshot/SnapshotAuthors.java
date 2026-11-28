package club.boyuan.official.domain.interview.snapshot;

import static club.boyuan.official.domain.interview.snapshot.SnapshotRequest.JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Node decodes Yjs and identifies changed cells. Independently reconstruct the permitted
 * attribution and materialized changes from the locked database baseline and current directory of
 * cells.
 */
final class SnapshotAuthors {
    private SnapshotAuthors() {}

    static void verify(
            ObjectNode before,
            SnapshotRequest request,
            int actor,
            Set<Integer> roster,
            Map<Integer, Integer> dimensions) {
        ObjectNode rows = (ObjectNode) before.path("rows").deepCopy();
        for (var entry : request.changes().entrySet()) {
            int id = entry.getKey();
            if (!roster.contains(id)) throw SnapshotFailure.denied();
            String key = Integer.toString(id);
            JsonNode proposed = request.authors().path("rows").path(key);
            if (!proposed.isObject() || !proposed.path("materialized").isObject())
                throw SnapshotFailure.invalid();
            ObjectNode row = rows.with(key);
            JsonNode incoming = proposed.get("materialized");
            boolean historical = row.has("materialized");
            ObjectNode item =
                    historical ? (ObjectNode) row.get("materialized").deepCopy() : emptyItem(id);
            ObjectNode cells = row.with("cells"), writers = row.with("dimensionWriters");
            TreeSet<Integer> people = new TreeSet<>();
            row.path("contributors").forEach(person -> people.add(person.intValue()));
            people.add(actor);
            var contributors = JSON.valueToTree(people);
            row.set("contributors", contributors);
            for (String cell : entry.getValue()) {
                cells.put(cell, actor);
                var dim = SnapshotRequest.DIMENSION.matcher(cell);
                if (dim.matches()) {
                    int dimension = SnapshotRequest.positiveId(dim.group(1));
                    Integer maximum = dimensions.get(dimension);
                    if (maximum == null) throw SnapshotFailure.denied();
                    String field = dim.group(2) == null ? "scores" : "dimensionNotes";
                    JsonNode value = incoming.path(field).get(dim.group(1));
                    if (value != null
                            && (dim.group(2) == null
                                    ? !value.isNumber()
                                            || value.decimalValue().signum() < 0
                                            || value.decimalValue()
                                                            .compareTo(
                                                                    java.math.BigDecimal.valueOf(
                                                                            maximum))
                                                    > 0
                                    : !value.isTextual())) throw SnapshotFailure.invalid();
                    ObjectNode values = item.with(field);
                    if (value == null) values.remove(dim.group(1));
                    else values.set(dim.group(1), value);
                    writers.put(dim.group(1), actor);
                } else if (cell.equals("status")) {
                    JsonNode status = incoming.get("status");
                    if (!oneOf(status, 1, 2)) throw SnapshotFailure.invalid();
                    if (!historical || item.path("status").intValue() != status.intValue()) {
                        if (status.intValue() == 2) {
                            JsonNode stamp = proposed.path("submittedAt");
                            try {
                                Instant.parse(stamp.asText(""));
                            } catch (DateTimeParseException error) {
                                throw SnapshotFailure.invalid();
                            }
                            row.put("submittedBy", actor).set("submittedAt", stamp);
                        } else {
                            row.putNull("submittedBy").putNull("submittedAt");
                        }
                    }
                    item.set("status", status);
                } else {
                    JsonNode value = incoming.get(cell);
                    if (value == null
                            || (!value.isNull()
                                    && (cell.equals("comment")
                                            ? !value.isTextual()
                                            : !oneOf(value, 1, 2, 3)))) {
                        throw SnapshotFailure.invalid();
                    }
                    item.set(cell, value);
                }
            }
            row.put("lastEditedBy", actor).put("editedRevision", request.next());
            item.put("lastEditedBy", actor).put("version", request.next());
            item.set("contributors", contributors);
            item.set("dimensionWriters", writers);
            item.set("submittedBy", row.get("submittedBy"));
            item.set("submittedAt", row.get("submittedAt"));
            row.set("materialized", item);
        }
        if (!SnapshotRequest.canonical(rows, 0)
                .equals(SnapshotRequest.canonical(request.authors().get("rows"), 0))) {
            throw SnapshotFailure.invalid();
        }
    }

    static boolean alreadyCommittedBy(ObjectNode authors, SnapshotRequest request, int actor) {
        Set<Integer> edited = new HashSet<>();
        var rows = authors.path("rows").fields();
        while (rows.hasNext()) {
            var entry = rows.next();
            JsonNode row = entry.getValue();
            if (row.path("editedRevision").asLong(-1) != request.next()) continue;
            int id = SnapshotRequest.positiveId(entry.getKey());
            var cells = request.changes().get(id);
            if (row.path("lastEditedBy").asInt() != actor
                    || cells == null
                    || cells.stream()
                            .anyMatch(cell -> row.path("cells").path(cell).asInt() != actor))
                return false;
            edited.add(id);
        }
        return !edited.isEmpty() && edited.equals(request.changes().keySet());
    }

    private static ObjectNode emptyItem(int id) {
        ObjectNode item =
                JSON.createObjectNode()
                        .put("scheduleId", id)
                        .put("status", 1)
                        .putNull("comment")
                        .putNull("recommendation");
        item.putObject("scores");
        item.putObject("dimensionNotes");
        return item;
    }

    private static boolean oneOf(JsonNode value, int... permitted) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) return false;
        for (int candidate : permitted) if (value.intValue() == candidate) return true;
        return false;
    }
}
