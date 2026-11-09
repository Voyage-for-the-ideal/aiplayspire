package battleaimod.strategy;

import battleaimod.evaluation.EvaluationResult;
import battleaimod.evaluation.TacticalEvaluator;
import com.google.gson.*;
import org.junit.Assume;
import org.junit.Test;
import savestate.SaveState;
import savestate.StateFactories;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/** Optional real-snapshot extraction check, independent of the rendering engine. */
public class RealSaveEvaluationTest {
    @Test public void fourRolesAndEncounterKindsEvaluateWithoutLoadingTheEngine() throws Exception {
        String manifestPath = System.getProperty("battleai.realStateManifest");
        Assume.assumeTrue("Supply -Dbattleai.realStateManifest=<benchmark manifest.json>", manifestPath != null);
        JsonArray manifest = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(manifestPath)), StandardCharsets.UTF_8)).getAsJsonArray();
        Map<String, Integer> roles = new HashMap<>();
        Set<String> kinds = new HashSet<>();
        for (JsonElement item : manifest) {
            JsonObject fixture = item.getAsJsonObject();
            String text = new String(Files.readAllBytes(Paths.get(fixture.get("path").getAsString())), StandardCharsets.UTF_8);
            JsonElement raw = new JsonParser().parse(text);
            registerCards(raw);
            SaveState snapshot = SaveState.forFile(fixture.get("path").getAsString());
            EvaluationResult first = TacticalEvaluator.evaluate(snapshot, snapshot, snapshot.getPlayerHealth());
            EvaluationResult second = TacticalEvaluator.evaluate(snapshot, snapshot, snapshot.getPlayerHealth());
            assertEquals(first.toString(), second.toString());
            assertEquals(first.survivalScore + first.damageProgressScore + first.lethalScore
                    + first.threatScore + first.scalingScore + first.resourceScore + first.encounterScore
                    + first.mechanismScore, first.totalScore);
            assertTrue(Math.abs(first.mechanismScore) <= MechanismScore.LIMIT);
            String role = fixture.get("role").getAsString();
            assertEquals(role, snapshot.playerState.chosenClass.name());
            roles.put(role, roles.getOrDefault(role, 0) + 1);
            kinds.add(fixture.get("kind").getAsString());
        }
        for (String role : Arrays.asList("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")) {
            assertTrue(role + " must have at least four snapshots", roles.getOrDefault(role, 0) >= 4);
        }
        assertTrue(kinds.containsAll(Arrays.asList("NORMAL", "ELITE", "BOSS")));
    }

    private void registerCards(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("card_id")) {
                String id = object.get("card_id").getAsString();
                if (!StateFactories.cardIdToIndexMap.containsKey(id)) {
                    List<String> ids = new ArrayList<>();
                    if (StateFactories.cardIds != null) ids.addAll(Arrays.asList(StateFactories.cardIds));
                    StateFactories.cardIdToIndexMap.put(id, ids.size());
                    ids.add(id); StateFactories.cardIds = ids.toArray(new String[0]);
                }
            }
            for (Map.Entry<String, JsonElement> field : object.entrySet()) registerCards(field.getValue());
        } else if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) registerCards(element);
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            for (String part : value.getAsString().split(";;;")) {
                if (part.startsWith("{") || part.startsWith("[")) {
                    try { registerCards(new JsonParser().parse(part)); } catch (JsonParseException ignored) { }
                }
            }
        }
    }
}
