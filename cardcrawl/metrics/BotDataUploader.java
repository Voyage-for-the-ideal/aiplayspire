package com.megacrit.cardcrawl.metrics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.ExceptionHandler;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

public class BotDataUploader
        implements Runnable {
    private static final Logger logger = LogManager.getLogger(BotDataUploader.class.getName());
    private Gson gson = new Gson();
    private String url = System.getenv("STS_DATA_UPLOAD_URL");

    private GameDataType type;

    private String header;
    private ArrayList<String> data;

    public enum GameDataType {
        BANDITS, CRASH_DATA, DAILY_DATA, DEMO_EMBARK, VICTORY_DATA, CARD_DATA, ENEMY_DATA, RELIC_DATA, POTION_DATA, DAILY_MOD_DATA, BLIGHT_DATA, KEYWORD_DATA;
    }

    public void setValues(GameDataType type, String header, ArrayList<String> data) {
        this.type = type;
        this.header = header;
        this.data = data;
    }

    public void sendPost(HashMap<String, Serializable> eventData) {
        if (this.url == null) {
            return;
        }
        HashMap<String, Serializable> event = new HashMap<>();

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(hostname.getBytes());
        } catch (UnknownHostException | java.security.NoSuchAlgorithmException e) {
            e.printStackTrace();
            ExceptionHandler.handleException(e, logger);
        }
        eventData.put("STS_DATA_UPLOAD_KEY", System.getenv("STS_DATA_UPLOAD_KEY"));
        event.putAll(eventData);

        String data = this.gson.toJson(event);
        logger.info("UPLOADING TO LEADER BOARD: " + data);
        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        Net.HttpRequest httpRequest = requestBuilder.newRequest().method("POST").url(this.url)
                .header("Content-Type", "application/json").header("Accept", "application/json").build();
        httpRequest.setContent(data);
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                if (Settings.isDev) {
                    BotDataUploader.logger.info("Bot Data Upload: http request response: " + httpResponse
                            .getStatus().getStatusCode() + " "
                            + httpResponse
                                    .getResultAsString());
                }
            }

            public void failed(Throwable t) {
                if (Settings.isDev) {
                    BotDataUploader.logger.info("Bot Data Upload: http request failed: " + t.toString());
                }
            }

            public void cancelled() {
                if (Settings.isDev) {
                    BotDataUploader.logger.info("Bot Data Upload: http request cancelled.");
                }
            }
        });
    }

    private void sendData(String table, String header, ArrayList<String> entries) {
        HashMap<String, Serializable> data = new HashMap<>(entries.size() + 1);
        data.put("event type", table + " data update");
        data.put("header", header);
        data.put("data", entries);
        sendPost(data);
    }

    public static void uploadDataAsync(GameDataType type, String header, ArrayList<String> data) {
        BotDataUploader poster = new BotDataUploader();
        poster.setValues(type, header, data);
        Thread t = new Thread(poster);
        t.setName("LeaderboardPoster");
        t.start();
    }

    public void run() {
        switch (this.type) {
            case CARD_DATA:
                sendData("card", this.header, this.data);
                return;
            case RELIC_DATA:
                sendData("relic", this.header, this.data);
                return;
            case ENEMY_DATA:
                sendData("enemy", this.header, this.data);
                return;
            case POTION_DATA:
                sendData("potion", this.header, this.data);
                return;
            case DAILY_MOD_DATA:
                sendData("daily mod", this.header, this.data);
                return;
            case BLIGHT_DATA:
                sendData("blight", this.header, this.data);
                return;
            case KEYWORD_DATA:
                sendData("keywords", this.header, this.data);
                return;
        }
        logger.info("Unspecified/deprecated LeaderboardPosterType: " + this.type.name() + " in run()");
    }

    public static void uploadKeywordData() {
        TreeMap<String, String> keywords = GameDictionary.keywords;
        if (keywords.isEmpty()) {
            GameDictionary.initialize();
        }

        ArrayList<String> data = new ArrayList<>();
        for (String name : keywords.keySet()) {
            data.add(String.format("%s\t%s", new Object[] {name, keywords.get(name) }));
        }
        uploadDataAsync(GameDataType.KEYWORD_DATA, "name\ttext", data);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\metrics\
 * BotDataUploader.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

