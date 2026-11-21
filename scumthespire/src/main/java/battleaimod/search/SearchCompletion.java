package battleaimod.search;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Request-local terminal signal. A notification before await is never lost. */
public final class SearchCompletion {
    private final CountDownLatch terminal = new CountDownLatch(1);
    private volatile Throwable failure;
    private volatile boolean cancelled;

    public synchronized void complete() {
        finish(null, false);
    }

    public synchronized void fail(Throwable error) {
        if (error == null) throw new IllegalArgumentException("failure requires a cause");
        finish(error, false);
    }

    public synchronized void cancel() {
        finish(null, true);
    }

    private void finish(Throwable error, boolean cancel) {
        if (isReady()) return;
        failure = error;
        cancelled = cancel;
        terminal.countDown();
    }

    public boolean await(long progressIntervalMillis) throws InterruptedException {
        return terminal.await(Math.max(0L, progressIntervalMillis), TimeUnit.MILLISECONDS);
    }

    public boolean isReady() { return terminal.getCount() == 0; }
    public Throwable failure() { return failure; }
    public boolean isCancelled() { return cancelled; }
}
