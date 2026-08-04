package battleaimod.search;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchProfileTest {
    @Test
    public void balancedIsTheSafeDefault() {
        assertEquals(SearchProfile.BALANCED, SearchProfile.fromString(null));
        assertEquals(SearchProfile.BALANCED, SearchProfile.fromString("UNKNOWN"));
    }

    @Test
    public void onlyDeepWaitsForTheFinalPath() {
        assertTrue(SearchProfile.FAST.streamCommands());
        assertTrue(SearchProfile.BALANCED.streamCommands());
        assertFalse(SearchProfile.DEEP.streamCommands());
    }
}
