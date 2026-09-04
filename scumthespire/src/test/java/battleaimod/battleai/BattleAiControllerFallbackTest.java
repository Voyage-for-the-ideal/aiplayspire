package battleaimod.battleai;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public class BattleAiControllerFallbackTest {
    @Test
    public void prefersDeathLineWhenNoProgress() {
        StateNode start = new StateNode(null, null, null);
        StateNode death = new StateNode(new StateNode(null, null, null), null, null);

        assertSame(death, BattleAiController.noWinFallback(start, death));
    }

    @Test
    public void keepsStartStateWhenNoDeathRecorded() {
        StateNode start = new StateNode(null, null, null);

        assertSame(start, BattleAiController.noWinFallback(start, null));
    }

    @Test
    public void keepsProgressFallbackEvenWithDeathLine() {
        StateNode start = new StateNode(null, null, null);
        StateNode progressed = new StateNode(start, null, null);
        StateNode death = new StateNode(start, null, null);

        assertSame(progressed, BattleAiController.noWinFallback(progressed, death));
    }

    @Test
    public void ignoresRootDeathNodeWithoutCommands() {
        StateNode start = new StateNode(null, null, null);
        StateNode rootDeath = new StateNode(null, null, null);

        assertSame(start, BattleAiController.noWinFallback(start, rootDeath));
    }
}
