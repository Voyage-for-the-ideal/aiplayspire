package battleaimod.search;

import battleaimod.networking.BackendEndpoint;
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

    @Test
    public void balancedExtendsOnlyItsJavaDeadline() {
        assertEquals(5_000, SearchProfile.FAST.maxExpansions());
        assertEquals(10_000L, SearchProfile.FAST.timeoutMillis());
        assertEquals(15_000, SearchProfile.BALANCED.maxExpansions());
        assertEquals(90_000L, SearchProfile.BALANCED.timeoutMillis());
        assertEquals(50_000, SearchProfile.DEEP.maxExpansions());
        assertEquals(90_000L, SearchProfile.DEEP.timeoutMillis());
    }

    @Test
    public void nativeDeadlinesRemainIndependent() {
        assertEquals(250L, SearchProfile.FAST.nativeTimeoutMillis());
        assertEquals(2_000L, SearchProfile.BALANCED.nativeTimeoutMillis());
        assertEquals(10_000L, SearchProfile.DEEP.nativeTimeoutMillis());
    }

    @Test
    public void balancedDeadlineFitsClientValidationAndRetainsExplicitOverride() {
        String previous = System.getProperty("battleai.clientBudgetMs");
        try {
            System.clearProperty("battleai.clientBudgetMs");
            assertEquals(90_000L, BackendEndpoint.timeoutMillis(SearchProfile.BALANCED.timeoutMillis()));
            System.setProperty("battleai.clientBudgetMs", "30000");
            assertEquals(30_000L, BackendEndpoint.timeoutMillis(SearchProfile.BALANCED.timeoutMillis()));
        } finally {
            if (previous == null) System.clearProperty("battleai.clientBudgetMs");
            else System.setProperty("battleai.clientBudgetMs", previous);
        }
    }
}
