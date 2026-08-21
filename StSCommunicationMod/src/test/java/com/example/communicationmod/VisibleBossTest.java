package com.example.communicationmod;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Decision-time gating of the visible boss (plan section 30).
 *
 * Drives GameStateConverter.visibleBossFor, the pure rule function that
 * getVisibleBoss() runs on every serialized state.  The two room-typed flags
 * come from instanceof checks in getVisibleBoss(); the rules themselves (floor
 * 0, boss relic, completed boss combat, Act IV, A20 hidden second boss) are
 * covered here without needing the game's boot-time static state.
 */
public class VisibleBossTest {
    @Test
    public void normalActDecisionExposesTheMappedBoss() {
        assertEquals("Hexaghost",
            GameStateConverter.visibleBossFor(true, 10, 1, false, false, "Hexaghost"));
    }

    @Test
    public void actTwoFirstDecisionExposesTheNewActBoss() {
        assertEquals("Champ",
            GameStateConverter.visibleBossFor(true, 17, 2, false, false, "Champ"));
    }

    @Test
    public void floorZeroIsNoBoss() {
        assertEquals("NO_BOSS",
            GameStateConverter.visibleBossFor(true, 0, 1, false, false, "Hexaghost"));
    }

    @Test
    public void notInARunIsNoBoss() {
        assertEquals("NO_BOSS",
            GameStateConverter.visibleBossFor(false, 10, 1, false, false, "Hexaghost"));
    }

    @Test
    public void bossRelicSelectionIsNoBoss() {
        assertEquals("NO_BOSS",
            GameStateConverter.visibleBossFor(true, 17, 2, true, false, "Hexaghost"));
    }

    @Test
    public void completedBossCombatRewardIsNoBoss() {
        // Boss dead: rare-card reward still on screen, next Act's boss not
        // generated or revealed yet.
        assertEquals("NO_BOSS",
            GameStateConverter.visibleBossFor(true, 16, 1, false, true, "Hexaghost"));
    }

    @Test
    public void actFourIsCorruptHeart() {
        assertEquals("Corrupt Heart",
            GameStateConverter.visibleBossFor(true, 51, 4, false, false, "Corrupt Heart"));
    }

    @Test
    public void nullBossKeyIsNoBoss() {
        // Transition frame before the next dungeon sets its boss.
        assertEquals("NO_BOSS",
            GameStateConverter.visibleBossFor(true, 10, 1, false, false, null));
    }

    @Test
    public void a20NeverLeaksTheHiddenSecondBoss() {
        // Release-blocking regression test: AbstractDungeon.bossList never
        // enters this path at all -- the serialized field is a single boss id.
        // Even when the game's A20 bossList holds all three Act III candidates,
        // the visible field must equal only the first boss.
        String visible = GameStateConverter.visibleBossFor(true, 40, 3, false, false, "Time Eater");
        assertEquals("Time Eater", visible);

        List<String> hidden = Arrays.asList("Awakened One", "Donu and Deca");
        for (String boss : hidden) {
            assertFalse("hidden second boss leaked: " + boss, visible.contains(boss));
        }
        assertFalse(visible.contains("bossList"));
    }
}
