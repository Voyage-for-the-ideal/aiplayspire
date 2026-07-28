package com.megacrit.cardcrawl.integrations;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SteelSeries {
    private final Logger logger = LogManager.getLogger(SteelSeries.class.getName());
    private final String gameName = "SLAY_THE_SPIRE";
    public Boolean isEnabled;
    private String url;
    private long timeAtLastHealthcheck = 0L;

    public SteelSeries() {
        String program_data = System.getenv("PROGRAMDATA");
        Path winPath = Paths.get(program_data + "/SteelSeries/SteelSeries Engine 3/coreProps.json", new String[0]);
        Path macPath = Paths.get("/Library/Application Support/SteelSeries Engine 3/coreProps.json", new String[0]);
        Boolean winExists = Boolean.valueOf(Files.exists(winPath, new java.nio.file.LinkOption[0]));
        Boolean macExists = Boolean.valueOf(Files.exists(macPath, new java.nio.file.LinkOption[0]));
        this.isEnabled = Boolean.valueOf((winExists.booleanValue() || macExists.booleanValue()));
        this.logger.info("enabled=" + this.isEnabled);
        if (!this.isEnabled.booleanValue())
            return;
        String _url = winExists.booleanValue() ? getUrl(winPath) : getUrl(macPath);
        if (_url != null) {
            this.url = "http://" + _url;
        } else {
            this.logger.info("ERROR: url is null!");
        }
        register();
        create_event_handler();
    }

    private String getUrl(Path path) {
        Gson gson = new Gson();
        try {
            Reader reader = Files.newBufferedReader(path);
            Map<?, ?> map = (Map<?, ?>) gson.fromJson(reader, Map.class);
            reader.close();
            return (String) map.get("address");
        } catch (Exception e) {
            e.printStackTrace();
            this.isEnabled = Boolean.valueOf(false);

            return null;
        }
    }

    public void update() {
        if (System.currentTimeMillis() - this.timeAtLastHealthcheck > 14000L) {
            doHealthCheck();
            this.timeAtLastHealthcheck = System.currentTimeMillis();
        }
    }

    private void doHealthCheck() {
        if (!this.isEnabled.booleanValue())
            return;
        Map<String, Object> data = new HashMap<>();
        data.put("game", "SLAY_THE_SPIRE");
        sendPost(this.url + "/game_heartbeat", data);
    }

    private void register() {
        if (!this.isEnabled.booleanValue())
            return;
        Map<String, Object> data = new HashMap<>();
        data.put("game", "SLAY_THE_SPIRE");
        data.put("game_display_name", "Slay the Spire");
        data.put("developer", "MEGACRIT");
        sendPost(this.url + "/game_metadata", data);
    }

    private Map<String, Object> create_event_map(AbstractPlayer.PlayerClass character, Map<String, Integer> color) {
        Map<String, Object> c1 = new HashMap<>();
        c1.put("red", Integer.valueOf(0));
        c1.put("green", Integer.valueOf(0));
        c1.put("blue", Integer.valueOf(0));
        Map<String, Object> gradient = new HashMap<>();
        gradient.put("zero", c1);
        gradient.put("hundred", color);
        Map<String, Object> colorConfig = new HashMap<>();
        colorConfig.put("gradient", gradient);

        Map<String, Object> keyboardHandler = new HashMap<>();
        keyboardHandler.put("device-type", "keyboard");
        keyboardHandler.put("zone", "all");
        keyboardHandler.put("color", colorConfig);
        keyboardHandler.put("mode", "percent");

        Map<String, Object> mouseHandler = new HashMap<>();
        mouseHandler.put("device-type", "mouse");
        mouseHandler.put("zone", "all");
        mouseHandler.put("color", colorConfig);
        mouseHandler.put("mode", "percent");

        List<Map<String, Object>> handlers = new ArrayList<>();
        handlers.add(keyboardHandler);
        handlers.add(mouseHandler);
        Map<String, Object> data = new HashMap<>();
        data.put("game", "SLAY_THE_SPIRE");
        data.put("event", character.toString());
        data.put("min_value", Integer.valueOf(0));
        data.put("max_value", Integer.valueOf(100));
        data.put("icon_id", Integer.valueOf(0));
        data.put("handlers", handlers);
        return data;
    }

    private void create_event_handler() {
        if (!this.isEnabled.booleanValue())
            return;
        Map<String, Integer> ironclad_color = new HashMap<>();
        ironclad_color.put("red", Integer.valueOf(255));
        ironclad_color.put("green", Integer.valueOf(0));
        ironclad_color.put("blue", Integer.valueOf(0));
        sendPost(this.url + "/bind_game_event", create_event_map(AbstractPlayer.PlayerClass.IRONCLAD, ironclad_color));

        Map<String, Integer> silent_color = new HashMap<>();
        silent_color.put("red", Integer.valueOf(0));
        silent_color.put("green", Integer.valueOf(255));
        silent_color.put("blue", Integer.valueOf(0));
        sendPost(this.url + "/bind_game_event", create_event_map(AbstractPlayer.PlayerClass.THE_SILENT, silent_color));

        Map<String, Integer> defect_color = new HashMap<>();
        defect_color.put("red", Integer.valueOf(0));
        defect_color.put("green", Integer.valueOf(0));
        defect_color.put("blue", Integer.valueOf(255));
        sendPost(this.url + "/bind_game_event", create_event_map(AbstractPlayer.PlayerClass.DEFECT, defect_color));

        Map<String, Integer> watcher_color = new HashMap<>();
        watcher_color.put("red", Integer.valueOf(148));
        watcher_color.put("green", Integer.valueOf(0));
        watcher_color.put("blue", Integer.valueOf(211));
        sendPost(this.url + "/bind_game_event", create_event_map(AbstractPlayer.PlayerClass.WATCHER, watcher_color));
    }

    public void event_character_chosen(AbstractPlayer.PlayerClass character) {
        if (!this.isEnabled.booleanValue())
            return;
        Map<String, Object> value = new HashMap<>();
        value.put("value", Integer.valueOf(100));
        Map<String, Object> data = new HashMap<>();
        data.put("game", "SLAY_THE_SPIRE");
        data.put("event", character.toString());
        data.put("data", value);
        sendPost(this.url + "/game_event", data);
    }

    private void sendPost(String url, Map<String, Object> data) {
        Gson gson = new Gson();
        String content = gson.toJson(data);
        this.logger.info("HTTP Request: url=" + url + " data=" + content);
        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        Net.HttpRequest httpRequest = requestBuilder.newRequest().method("POST").url(url)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("User-Agent", "sts/" + CardCrawlGame.TRUE_VERSION_NUM).build();
        httpRequest.setContent(content);
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                SteelSeries.this.logger.info("http request status: " + httpResponse
                        .getStatus().getStatusCode() + " response: "
                        + httpResponse
                                .getResultAsString());
            }

            public void failed(Throwable t) {
                SteelSeries.this.logger.info("http request failed: " + t.toString());
            }

            public void cancelled() {
                SteelSeries.this.logger.info("http request cancelled.");
            }
        });
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\integrations\
 * SteelSeries.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

