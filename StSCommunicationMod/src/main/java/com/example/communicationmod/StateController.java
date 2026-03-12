package com.example.communicationmod;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StateController {
    private static final AtomicReference<CountDownLatch> requestLatch = new AtomicReference<>(null);
    private static volatile String lastStateJson = "{\"error\": \"State not available\"}";

    // Called by Game Thread
    public static void updateState() {
        CountDownLatch latch = requestLatch.get();
        if (latch != null) {
            try {
                // Generate state on game thread
                String newState = GameStateConverter.getGameStateJson();
                if (newState != null && !newState.isEmpty()) {
                    lastStateJson = newState;
                }
            } catch (Exception e) {
                e.printStackTrace();
                lastStateJson = "{\"error\": \"Failed to generate state: " + e.getMessage() + "\"}";
            } finally {
                // Signal waiting thread
                latch.countDown();
                requestLatch.set(null);
            }
        }
    }

    // Called by HTTP Thread
    public static String requestState() {
        CountDownLatch latch = new CountDownLatch(1);
        if (requestLatch.compareAndSet(null, latch)) {
            try {
                // Wait for game thread to process (timeout 200ms - enough for a few frames)
                if (latch.await(200, TimeUnit.MILLISECONDS)) {
                    return lastStateJson;
                } else {
                    return "{\"error\": \"Request timed out waiting for game loop\"}";
                }
            } catch (InterruptedException e) {
                return "{\"error\": \"Request interrupted\"}";
            } finally {
                requestLatch.set(null); // Cleanup if timeout
            }
        } else {
            // Another request is pending, just return last known state or busy
            return "{\"error\": \"Server busy\"}";
        }
    }
}
