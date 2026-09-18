package com.example.communicationmod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MapProtocolFieldsTest {
    @Test
    public void eventChancesMirrorBaseWeightsWithoutModifiers() {
        Map<String, Double> chances = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                false,
                false,
                false,
                false,
                false,
                false
        );

        assertEquals(0.00, chances.get("elite"), 1e-9);
        assertEquals(0.10, chances.get("monster"), 1e-9);
        assertEquals(0.03, chances.get("shop"), 1e-9);
        assertEquals(0.02, chances.get("treasure"), 1e-9);
        assertEquals(0.85, chances.get("event"), 1e-9);
    }

    @Test
    public void eventChancesNoEliteBeforeFloorSixEvenWithDeadly() {
        Map<String, Double> chances = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                5,
                false,
                true,
                false,
                false,
                false,
                false
        );

        assertEquals(0.00, chances.get("elite"), 1e-9);
        assertEquals(0.10, chances.get("monster"), 1e-9);
        assertEquals(0.03, chances.get("shop"), 1e-9);
        assertEquals(0.02, chances.get("treasure"), 1e-9);
        assertEquals(0.85, chances.get("event"), 1e-9);
    }

    @Test
    public void deadlyEventsUsesTheGamesTwoEliteBucketsAfterFloorSix() {
        Map<String, Double> chances = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                false,
                true,
                false,
                false,
                false,
                false
        );

        assertEquals(0.20, chances.get("elite"), 1e-9);
        assertEquals(0.10, chances.get("monster"), 1e-9);
        assertEquals(0.03, chances.get("shop"), 1e-9);
        assertEquals(0.02, chances.get("treasure"), 1e-9);
        assertEquals(0.65, chances.get("event"), 1e-9);
    }

    @Test
    public void eventChancesConvertsMonstersToEventsWithJuzuBracelet() {
        Map<String, Double> chances = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                false,
                false,
                false,
                true,
                false,
                false
        );

        assertEquals(0.00, chances.get("elite"), 1e-9);
        assertEquals(0.00, chances.get("monster"), 1e-9);
        assertEquals(0.03, chances.get("shop"), 1e-9);
        assertEquals(0.02, chances.get("treasure"), 1e-9);
        assertEquals(0.95, chances.get("event"), 1e-9);
    }

    @Test
    public void eventChancesSuppressesShopImmediatelyAfterShopRoom() {
        Map<String, Double> chances = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                true,
                false,
                false,
                false,
                false,
                false
        );

        assertEquals(0.00, chances.get("shop"), 1e-9);
        assertEquals(0.88, chances.get("event"), 1e-9);
    }

    @Test
    public void eventChancesForcesTreasureWithTinyChest() {
        Map<String, Double> chances = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                false,
                true,
                true,
                false,
                false,
                false
        );

        assertEquals(0.00, chances.get("elite"), 1e-9);
        assertEquals(0.00, chances.get("monster"), 1e-9);
        assertEquals(0.00, chances.get("shop"), 1e-9);
        assertEquals(1.00, chances.get("treasure"), 1e-9);
        assertEquals(0.00, chances.get("event"), 1e-9);
    }

    @Test
    public void mimicInfestationConvertsTreasureToEliteUnlessJuzu() {
        Map<String, Double> chancesWithoutJuzu = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                false,
                false,
                false,
                false,
                true,
                true
        );

        assertEquals(0.02, chancesWithoutJuzu.get("elite"), 1e-9);

        Map<String, Double> chancesWithJuzu = GameStateConverter.resolveEventChances(
                buildChances(0.10f, 0.10f, 0.03f, 0.02f),
                10,
                false,
                false,
                false,
                true,
                true,
                true
        );

        assertEquals(0.97, chancesWithJuzu.get("event"), 1e-9);
        assertEquals(0.00, chancesWithJuzu.get("elite"), 1e-9);
        assertEquals(0.00, chancesWithJuzu.get("treasure"), 1e-9);
    }

    @Test
    public void pathHistoryUsesOnlyCompletedPairs() {
        List<String> selected = new ArrayList<>();
        selected.add("M");
        selected.add("R");
        selected.add("?");

        List<String> resolved = new ArrayList<>();
        resolved.add("M");
        resolved.add("R");

        List<java.util.Map<String, Object>> history = GameStateConverter.buildPathHistory(selected, resolved);
        assertEquals(2, history.size());
        assertEquals(1, ((Number) history.get(0).get("floor")).intValue());
        assertEquals("M", history.get(0).get("selected_symbol"));
        assertEquals("M", history.get(0).get("resolved_symbol"));
        assertEquals(2, ((Number) history.get(1).get("floor")).intValue());
        assertEquals("R", history.get(1).get("selected_symbol"));
        assertEquals("R", history.get(1).get("resolved_symbol"));
    }

    @Test
    public void pathHistoryIsDeterministicForEmptyInput() {
        assertEquals(0, GameStateConverter.buildPathHistory(new ArrayList<String>(), new ArrayList<String>()).size());
    }

    private static ArrayList<Float> buildChances(float elite, float monster, float shop, float treasure) {
        ArrayList<Float> result = new ArrayList<>();
        result.add(elite);
        result.add(monster);
        result.add(shop);
        result.add(treasure);
        return result;
    }
}
