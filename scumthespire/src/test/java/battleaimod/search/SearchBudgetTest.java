package battleaimod.search;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchBudgetTest {
    @Test
    public void expansionLimitIsMonotonicAndInclusive() {
        SearchBudget budget = new SearchBudget(10, 1_000L, () -> 0L);

        assertFalse(budget.isExpansionLimitReached(9));
        assertTrue(budget.isExpansionLimitReached(10));
        assertTrue(budget.isExpansionLimitReached(11));
    }

    @Test
    public void timeoutUsesInjectedMonotonicClock() {
        AtomicLong clock = new AtomicLong(1_000L);
        SearchBudget budget = new SearchBudget(10, 30L, clock::get);

        clock.addAndGet(29_999_999L);
        assertFalse(budget.isTimedOut());
        clock.incrementAndGet();
        assertTrue(budget.isTimedOut());
    }

    @Test
    public void balancedContinuesPastThirtySecondsButStillStopsAtItsLimits() {
        AtomicLong clock = new AtomicLong();
        SearchProfile profile = SearchProfile.BALANCED;
        SearchBudget budget = new SearchBudget(profile.maxExpansions(), profile.timeoutMillis(), clock::get);

        clock.set(30_000_000_000L);
        assertFalse(budget.isTimedOut());
        assertFalse(budget.isExpansionLimitReached(14_999));
        assertTrue(budget.isExpansionLimitReached(15_000));
        clock.set(89_999_999_999L);
        assertFalse(budget.isTimedOut());
        clock.incrementAndGet();
        assertTrue(budget.isTimedOut());
    }
}
