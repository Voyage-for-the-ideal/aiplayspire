package com.example.communicationmod;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Classification and serialization rules for the death/victory settlement
 * chain (the bug fixed in 1.4.1). When the player dies, isInARun() is already
 * false while the settlement screens still run in GAMEPLAY mode; those screens
 * must be reported as clickable GAME_OVER states, not as an out-of-run error.
 *
 * Drives the pure rule functions in ChoiceScreenUtils/GameStateConverter the
 * same way VisibleBossTest drives GameStateConverter.visibleBossFor: without
 * booting a game session.
 */
public class GameOverScreenRulesTest {

    @Test
    public void fourSettlementScreensAreRecognized() {
        assertTrue(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.DEATH));
        assertTrue(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.VICTORY));
        assertTrue(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.UNLOCK));
        assertTrue(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.NEOW_UNLOCK));
    }

    @Test
    public void inRunScreensAreNotSettlement() {
        assertFalse(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.CARD_REWARD));
        assertFalse(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.MAP));
        assertFalse(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.GRID));
        assertFalse(ChoiceScreenUtils.isSettlementScreenType(AbstractDungeon.CurrentScreen.SHOP));
        assertFalse(ChoiceScreenUtils.isSettlementScreenType(null));
    }

    @Test
    public void reasonMapsDefeatVictoryUnlockAndUnknown() {
        assertEquals("defeat", ChoiceScreenUtils.settlementReasonFor(AbstractDungeon.CurrentScreen.DEATH));
        assertEquals("victory", ChoiceScreenUtils.settlementReasonFor(AbstractDungeon.CurrentScreen.VICTORY));
        assertEquals("unlock", ChoiceScreenUtils.settlementReasonFor(AbstractDungeon.CurrentScreen.UNLOCK));
        assertEquals("unlock", ChoiceScreenUtils.settlementReasonFor(AbstractDungeon.CurrentScreen.NEOW_UNLOCK));
        assertEquals("unknown", ChoiceScreenUtils.settlementReasonFor(null));
    }

    @Test
    public void deathAndVictoryAdvertiseReturnToMenuChoice() {
        // run_lifecycle clicks index 0; the list must stay exactly one entry.
        assertEquals(Arrays.asList("return_to_menu"),
                ChoiceScreenUtils.settlementChoicesFor(AbstractDungeon.CurrentScreen.DEATH));
        assertEquals(Arrays.asList("return_to_menu"),
                ChoiceScreenUtils.settlementChoicesFor(AbstractDungeon.CurrentScreen.VICTORY));
    }

    @Test
    public void unlockScreensAdvertiseConfirmChoice() {
        assertEquals(Arrays.asList("confirm"),
                ChoiceScreenUtils.settlementChoicesFor(AbstractDungeon.CurrentScreen.UNLOCK));
        assertEquals(Arrays.asList("confirm"),
                ChoiceScreenUtils.settlementChoicesFor(AbstractDungeon.CurrentScreen.NEOW_UNLOCK));
        assertTrue(ChoiceScreenUtils.settlementChoicesFor(null).isEmpty());
    }

    @Test
    public void settlementJsonCarriesFullPlayerContract() {
        // The python PlayerState declares current_hp/max_hp/block/energy/gold
        // non-optional: a partial player map would fail pydantic validation and
        // look like a lost connection. Lock the five-key contract here so a
        // future key removal fails the build.
        Map<String, Object> player = new HashMap<>();
        player.put("current_hp", 0);
        player.put("max_hp", 75);
        player.put("block", 0);
        player.put("energy", 0);
        player.put("gold", 100);

        Map<String, Object> state = GameStateConverter.settlementStateFields(
                "defeat", ChoiceScreenUtils.settlementChoicesFor(AbstractDungeon.CurrentScreen.DEATH),
                33, 3, "IRONCLAD", 15, player);

        assertEquals("GAME_OVER", state.get("screen_type"));
        assertFalse((Boolean) state.get("can_proceed"));
        assertFalse((Boolean) state.get("can_cancel"));
        assertEquals(33, state.get("floor"));
        assertEquals(3, state.get("act"));
        assertEquals("IRONCLAD", state.get("character"));
        assertEquals(15, state.get("ascension_level"));

        @SuppressWarnings("unchecked")
        Map<String, Object> reported = (Map<String, Object>) state.get("player");
        Set<String> expectedKeys = new HashSet<>(Arrays.asList(
                "current_hp", "max_hp", "block", "energy", "gold"));
        assertEquals(expectedKeys, reported.keySet());
    }

    @Test
    public void settlementJsonListsReturnToMenuForDefeat() {
        Map<String, Object> player = new HashMap<>();
        player.put("current_hp", 0);
        player.put("max_hp", 75);
        player.put("block", 0);
        player.put("energy", 0);
        player.put("gold", 100);

        Map<String, Object> state = GameStateConverter.settlementStateFields(
                "defeat", ChoiceScreenUtils.settlementChoicesFor(AbstractDungeon.CurrentScreen.DEATH),
                33, 3, "IRONCLAD", 15, player);

        assertEquals("defeat", state.get("game_over_reason"));
        @SuppressWarnings("unchecked")
        ArrayList<String> choices = (ArrayList<String>) state.get("choice_list");
        assertEquals(Arrays.asList("return_to_menu"), choices);
    }
}
