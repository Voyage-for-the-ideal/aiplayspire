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
}
