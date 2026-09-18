package ludicrousspeed.simulator.commands;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GridSelectionRulesTest {
    @Test
    public void allowsNonGlowingCardsWithoutATypeOrIdAllowlist() {
        // Ordinary cards, statuses, curses, and starter cards all use this
        // same non-glowing path in the production GRID enumerator.
        assertTrue(CommandList.isGridCardSelectable(false));
    }

    @Test
    public void excludesCardsAlreadySelectedByTheGridScreen() {
        assertFalse(CommandList.isGridCardSelectable(true));
    }
}
