package battleaimod.search;

import battleaimod.BattleAiMod;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class SearchCompletionTest {
    @Test public void completionBeforeWaitIsImmediateAndIdempotent() throws Exception {
        SearchCompletion signal = new SearchCompletion();
        signal.complete();
        signal.complete();
        signal.cancel();
        signal.fail(new IllegalStateException("late failure"));
        assertTrue(signal.await(0));
        assertTrue(signal.isReady());
        assertFalse(signal.isCancelled());
        assertNull(signal.failure());
    }

    @Test public void completionWakesWaiterAndPublishesResultFields() throws Exception {
        SearchCompletion signal = new SearchCompletion();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch enteringWait = new CountDownLatch(1);
        int[] result = {0};
        try {
            Future<Integer> waiting = executor.submit(() -> {
                enteringWait.countDown();
                assertTrue(signal.await(10_000));
                return result[0];
            });
            assertTrue(enteringWait.await(2, TimeUnit.SECONDS));
            result[0] = 42;
            signal.complete();
            assertEquals(42, (int) waiting.get(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test public void failureWakesWaiterAndKeepsItsCause() throws Exception {
        SearchCompletion signal = new SearchCompletion();
        RuntimeException cause = new IllegalStateException("search failed");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> waiting = executor.submit(() -> {
                assertTrue(signal.await(10_000));
                return signal.failure();
            });
            signal.fail(cause);
            assertSame(cause, waiting.get(2, TimeUnit.SECONDS));
            signal.complete();
            assertSame(cause, signal.failure());
            assertFalse(signal.isCancelled());
        } finally { executor.shutdownNow(); }
    }

    @Test public void cancellationIsTerminalAndWakesWaiter() throws Exception {
        SearchCompletion signal = new SearchCompletion();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> waiting = executor.submit(() -> signal.await(10_000));
            signal.cancel();
            assertTrue(waiting.get(2, TimeUnit.SECONDS));
            signal.complete();
            assertTrue(signal.isCancelled());
            assertNull(signal.failure());
        } finally { executor.shutdownNow(); }
    }

    @Test public void runningSearchRetainsProgressRateLimit() throws Exception {
        assertEquals(1500L, BattleAiMod.MESSAGE_TIME_MILLIS);
        SearchCompletion signal = new SearchCompletion();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch threeMessages = new CountDownLatch(3);
        try {
            Future<List<Long>> progress = executor.submit(() -> {
                List<Long> sent = new ArrayList<>();
                while (!signal.isReady()) {
                    sent.add(System.nanoTime());
                    threeMessages.countDown();
                    signal.await(25L);
                }
                return sent;
            });
            assertTrue(threeMessages.await(5, TimeUnit.SECONDS));
            signal.complete();
            List<Long> sent = progress.get(2, TimeUnit.SECONDS);
            assertTrue(sent.size() >= 3);
            for (int i = 1; i < sent.size(); i++) {
                assertTrue("progress should wait the full interval",
                    sent.get(i) - sent.get(i - 1) >= TimeUnit.MILLISECONDS.toNanos(25));
            }
        } finally { executor.shutdownNow(); }
    }

    @Test public void interruptPropagatesAndDoesNotCompleteSearch() throws Exception {
        SearchCompletion signal = new SearchCompletion();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch enteringWait = new CountDownLatch(1);
        try {
            Future<Boolean> interrupted = executor.submit(() -> {
                enteringWait.countDown();
                Thread.currentThread().interrupt();
                try { signal.await(10_000); return false; }
                catch (InterruptedException expected) { Thread.currentThread().interrupt(); return Thread.currentThread().isInterrupted(); }
            });
            assertTrue(enteringWait.await(2, TimeUnit.SECONDS));
            assertTrue(interrupted.get(2, TimeUnit.SECONDS));
            assertFalse(signal.isReady());
        } finally { executor.shutdownNow(); }
    }

    @Test public void progressTimeoutDoesNotMakeResultReady() throws Exception {
        SearchCompletion signal = new SearchCompletion();
        assertFalse(signal.await(0));
        assertFalse(signal.isReady());
        signal.complete();
        assertTrue(signal.await(0));
    }
}
