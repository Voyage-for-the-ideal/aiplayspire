package battleaimod.networking;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayRecoveryStateTest {
    @Test
    public void allowsOnlyOneRetryUntilReset() {
        ReplayRecoveryState state = new ReplayRecoveryState();

        assertTrue(state.tryUseRetry());
        assertFalse(state.tryUseRetry());

        state.reset();

        assertTrue(state.tryUseRetry());
    }
}
