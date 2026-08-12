package com.example.communicationmod;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameStateConverterTest {
    @Test
    public void preservesStructuredChoiceCardMetadata() {
        Map<String, Object> plain = GameStateConverter.choiceCardMetadata(
            4, "uuid-1", "Strike_R", "Strike", 0, true);
        assertEquals(4, plain.get("choice_index"));
        assertEquals("uuid-1", plain.get("uuid"));
        assertEquals("Strike_R", plain.get("id"));
        assertEquals("Strike", plain.get("name"));
        assertEquals(0, plain.get("upgrades"));
        assertTrue((Boolean) plain.get("can_upgrade"));

        Map<String, Object> upgraded = GameStateConverter.choiceCardMetadata(
            5, "uuid-1", "Strike_R", "Strike+", 1, false);
        assertEquals("uuid-1", upgraded.get("uuid"));
        assertEquals(1, upgraded.get("upgrades"));
        assertFalse((Boolean) upgraded.get("can_upgrade"));
        assertEquals("Strike+", upgraded.get("name"));
    }
}
