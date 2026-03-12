package com.example.communicationmod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.concurrent.Executors;

public class CommunicationServer {
    private HttpServer server;

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/state", new StateHandler());
        server.createContext("/action", new ActionHandler());
        server.createContext("/card_info", new CardInfoHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Communication Server started on port " + port);
    }

    static class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                // Request state from the game thread safely
                String response = StateController.requestState();
                byte[] responseBytes = response.getBytes("UTF-8");
                
                t.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                t.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = t.getResponseBody();
                os.write(responseBytes);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String error = "{\"error\": \"Internal Server Error: " + e.getMessage() + "\"}";
                byte[] errorBytes = error.getBytes("UTF-8");
                t.sendResponseHeaders(500, errorBytes.length);
                OutputStream os = t.getResponseBody();
                os.write(errorBytes);
                os.close();
            }
        }
    }

    static class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if ("POST".equals(t.getRequestMethod())) {
                    Scanner scanner = new Scanner(t.getRequestBody()).useDelimiter("\\A");
                    String body = scanner.hasNext() ? scanner.next() : "";
                    
                    // Add to queue for main thread processing
                    ActionController.actionQueue.add(body);

                    String response = "{\"status\": \"queued\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } else {
                    String response = "{\"error\": \"Only POST allowed\"}";
                    t.sendResponseHeaders(405, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // If headers already sent, this might fail, but worth a try
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }
    }

    static class CardInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                // Support GET with query param ?id=CardID or POST with body
                String cardId = null;
                if ("GET".equals(t.getRequestMethod())) {
                    String query = t.getRequestURI().getQuery();
                    if (query != null && query.startsWith("id=")) {
                        cardId = query.substring(3);
                    }
                } else if ("POST".equals(t.getRequestMethod())) {
                    Scanner scanner = new Scanner(t.getRequestBody()).useDelimiter("\\A");
                    if (scanner.hasNext()) {
                        String body = scanner.next();
                        // Simple JSON parsing if body is {"id": "..."} or just raw ID if simple
                        // Assuming simple raw text for now or simple json
                        if (body.contains("\"id\":")) {
                             // Very basic parsing to avoid adding GSON dependency here if not needed
                             // But we have GSON.
                             // Let's assume the body is just the ID for simplicity or JSON
                             com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(body, com.google.gson.JsonObject.class);
                             if (json.has("id")) {
                                 cardId = json.get("id").getAsString();
                             }
                        }
                    }
                }

                if (cardId != null) {
                    String response = CardInfoController.requestCardInfo(cardId);
                    byte[] responseBytes = response.getBytes("UTF-8");
                    t.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    t.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = t.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } else {
                    String response = "{\"error\": \"Missing card ID\"}";
                    t.sendResponseHeaders(400, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }
    }
}
