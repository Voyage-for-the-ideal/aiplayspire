package com.example.communicationmod;

import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CardInfoController {
    private static final Gson gson = new Gson();
    
    // Request structure
    private static class Request {
        String cardId;
        BlockingQueue<String> responseChannel; // Where to send the response
        
        Request(String cardId) {
            this.cardId = cardId;
            this.responseChannel = new LinkedBlockingQueue<>();
        }
    }

    // Queue for requests from HTTP thread to Game thread
    private static final BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();

    // Called by HTTP Thread
    // Returns JSON response
    public static String requestCardInfo(String cardId) {
        Request req = new Request(cardId);
        try {
            requestQueue.put(req);
            // Wait for response (timeout 500ms)
            String response = req.responseChannel.poll(500, TimeUnit.MILLISECONDS);
            if (response == null) {
                return "{\"error\": \"Timeout waiting for game thread\"}";
            }
            return response;
        } catch (InterruptedException e) {
            return "{\"error\": \"Request interrupted\"}";
        }
    }

    // Called by Game Thread (e.g. in receivePostUpdate)
    public static void processRequests() {
        // Process all pending requests
        Request req;
        while ((req = requestQueue.poll()) != null) {
            try {
                Map<String, Object> info = GameStateConverter.getCardInfo(req.cardId);
                String json = gson.toJson(info);
                req.responseChannel.offer(json);
            } catch (Exception e) {
                e.printStackTrace();
                req.responseChannel.offer("{\"error\": \"Internal error: " + e.getMessage() + "\"}");
            }
        }
    }
}
