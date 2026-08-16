package battleaimod.search;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SearchMetricsTest {
    @Test
    public void encodesSearchHotPathTimings() {
        SearchMetrics metrics = new SearchMetrics();
        metrics.stateKeyNanos = 9_000_000L;
        metrics.stateKeyEncodeNanos = 4_000_000L;
        metrics.stateKeyCanonicalHashNanos = 5_000_000L;
        metrics.loadStateNanos = 7_000_000L;
        metrics.loadStateCount = 3L;
        metrics.snapshotNanos = 6_000_000L;
        metrics.snapshotCount = 2L;

        JsonObject json = metrics.jsonEncode(20L, "TEST");

        assertEquals(9L, json.get("state_key_ms").getAsLong());
        assertEquals(4L, json.get("state_key_encode_ms").getAsLong());
        assertEquals(5L, json.get("state_key_canonical_hash_ms").getAsLong());
        assertEquals(7L, json.get("load_state_ms").getAsLong());
        assertEquals(3L, json.get("load_state_count").getAsLong());
        assertEquals(6L, json.get("snapshot_ms").getAsLong());
        assertEquals(2L, json.get("snapshot_count").getAsLong());
    }

    @Test
    public void encodesEvaluationCounters() {
        SearchMetrics metrics = new SearchMetrics();
        metrics.evaluationCount = 1234L;
        metrics.evaluationNanos = 7_000_000L;

        JsonObject json = metrics.jsonEncode(20L, "TEST");

        assertEquals(1234L, json.get("evaluation_count").getAsLong());
        assertEquals(7L, json.get("evaluation_ms").getAsLong());
    }
}
