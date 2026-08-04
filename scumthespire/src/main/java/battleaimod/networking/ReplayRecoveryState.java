package battleaimod.networking;

final class ReplayRecoveryState {
    private boolean retryUsed;

    boolean tryUseRetry() {
        if (retryUsed) {
            return false;
        }
        retryUsed = true;
        return true;
    }

    void reset() {
        retryUsed = false;
    }
}
