package battleaimod.search;

import com.google.gson.JsonObject;

public final class SearchMetrics {
    public long expandedNodes;
    public long generatedTurnStates;
    public long uniqueTurnStates;
    public long duplicateTurnStates;
    public long stateKeyNanos;
    public long stateKeyEncodeNanos;
    public long stateKeyCanonicalHashNanos;
    public long loadStateNanos;
    public long loadStateCount;
    public long snapshotNanos;
    public long snapshotCount;
    public int maxQueueSize;
    public int deepestTurn;

    public JsonObject jsonEncode(long elapsedMillis, String stopReason) {
        JsonObject json = new JsonObject();
        json.addProperty("expanded_nodes", expandedNodes);
        json.addProperty("generated_turn_states", generatedTurnStates);
        json.addProperty("unique_turn_states", uniqueTurnStates);
        json.addProperty("duplicate_turn_states", duplicateTurnStates);
        json.addProperty("state_key_ms", stateKeyNanos / 1_000_000L);
        json.addProperty("state_key_encode_ms", stateKeyEncodeNanos / 1_000_000L);
        json.addProperty("state_key_canonical_hash_ms", stateKeyCanonicalHashNanos / 1_000_000L);
        json.addProperty("load_state_ms", loadStateNanos / 1_000_000L);
        json.addProperty("load_state_count", loadStateCount);
        json.addProperty("snapshot_ms", snapshotNanos / 1_000_000L);
        json.addProperty("snapshot_count", snapshotCount);
        json.addProperty("max_queue_size", maxQueueSize);
        json.addProperty("deepest_turn", deepestTurn);
        json.addProperty("elapsed_ms", elapsedMillis);
        json.addProperty("stop_reason", stopReason);
        return json;
    }
}
