package com.megacrit.cardcrawl.metrics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.helpers.Prefs;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class Metrics
        implements Runnable {
    private static final Logger logger = LogManager.getLogger(Metrics.class.getName());
    private HashMap<Object, Object> params = new HashMap<>();
    private Gson gson = new Gson();

    private long lastPlaytimeEnd;
    public static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

    public boolean death;
    public boolean trueVictory;
    public MonsterGroup monsters = null;
    public MetricRequestType type;

    public enum MetricRequestType {
        UPLOAD_METRICS, UPLOAD_CRASH, NONE;
    }

    public void setValues(boolean death, boolean trueVictor, MonsterGroup monsters, MetricRequestType type) {
        this.death = death;
        this.trueVictory = trueVictor;
        this.monsters = monsters;
        this.type = type;
    }

    private void sendPost(String fileName) {
    }

    private void addData(Object key, Object value) {
        this.params.put(key, value);
    }

    private void sendPost(String url, final String fileToDelete) {
        HashMap<String, Serializable> event = new HashMap<>();
        event.put("event", this.params);
        if (Settings.isBeta) {
            event.put("host", CardCrawlGame.playerName);
        } else {
            event.put("host", CardCrawlGame.alias);
        }
        event.put("time", Long.valueOf(System.currentTimeMillis() / 1000L));
        String data = this.gson.toJson(event);
        logger.info("UPLOADING METRICS TO: url=" + url + ",data=" + data);
        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        Net.HttpRequest httpRequest = requestBuilder.newRequest().method("POST").url(url)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("User-Agent", "curl/7.43.0").build();
        httpRequest.setContent(data);
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                Metrics.logger.info("Metrics: http request response: " + httpResponse.getResultAsString());
                if (fileToDelete != null) {
                    Gdx.files.local(fileToDelete).delete();
                }
            }

            public void failed(Throwable t) {
                Metrics.logger.info("Metrics: http request failed: " + t.toString());
            }

            public void cancelled() {
                Metrics.logger.info("Metrics: http request cancelled.");
            }
        });
    }

    private void gatherAllData(boolean death, boolean trueVictor, MonsterGroup monsters) {
        addData("play_id", UUID.randomUUID().toString());
        addData("build_version", CardCrawlGame.TRUE_VERSION_NUM);
        addData("seed_played", Settings.seed.toString());
        addData("chose_seed", Boolean.valueOf(Settings.seedSet));
        addData("seed_source_timestamp", Long.valueOf(Settings.seedSourceTimestamp));
        addData("is_daily", Boolean.valueOf(Settings.isDailyRun));
        addData("special_seed", Settings.specialSeed);
        if (ModHelper.enabledMods.size() > 0) {
            addData("daily_mods", ModHelper.getEnabledModIDs());
        }

        addData("is_trial", Boolean.valueOf(Settings.isTrial));
        addData("is_endless", Boolean.valueOf(Settings.isEndless));

        if (death) {
            AbstractPlayer player = AbstractDungeon.player;
            CardCrawlGame.metricData.current_hp_per_floor.add(Integer.valueOf(player.currentHealth));
            CardCrawlGame.metricData.max_hp_per_floor.add(Integer.valueOf(player.maxHealth));
            CardCrawlGame.metricData.gold_per_floor.add(Integer.valueOf(player.gold));
        }

        addData("is_ascension_mode", Boolean.valueOf(AbstractDungeon.isAscensionMode));
        addData("ascension_level", Integer.valueOf(AbstractDungeon.ascensionLevel));

        addData("neow_bonus", CardCrawlGame.metricData.neowBonus);
        addData("neow_cost", CardCrawlGame.metricData.neowCost);

        addData("is_beta", Boolean.valueOf(Settings.isBeta));
        addData("is_prod", Boolean.valueOf(Settings.isDemo));
        addData("victory", Boolean.valueOf(!death));
        addData("floor_reached", Integer.valueOf(AbstractDungeon.floorNum));
        if (trueVictor) {
            addData("score", Integer.valueOf(VictoryScreen.calcScore(!death)));
        } else {
            addData("score", Integer.valueOf(DeathScreen.calcScore(!death)));
        }
        this.lastPlaytimeEnd = System.currentTimeMillis() / 1000L;
        addData("timestamp", Long.valueOf(this.lastPlaytimeEnd));
        addData("local_time", timestampFormatter.format(Calendar.getInstance().getTime()));
        addData("playtime", Long.valueOf((long) CardCrawlGame.playtime));
        addData("player_experience", Long.valueOf(Settings.totalPlayTime));
        addData("master_deck", AbstractDungeon.player.masterDeck.getCardIdsForMetrics());
        addData("relics", AbstractDungeon.player.getRelicNames());
        addData("gold", Integer.valueOf(AbstractDungeon.player.gold));
        addData("campfire_rested", Integer.valueOf(CardCrawlGame.metricData.campfire_rested));
        addData("campfire_upgraded", Integer.valueOf(CardCrawlGame.metricData.campfire_upgraded));
        addData("purchased_purges", Integer.valueOf(CardCrawlGame.metricData.purchased_purges));
        addData("potions_floor_spawned", CardCrawlGame.metricData.potions_floor_spawned);
        addData("potions_floor_usage", CardCrawlGame.metricData.potions_floor_usage);
        addData("current_hp_per_floor", CardCrawlGame.metricData.current_hp_per_floor);
        addData("max_hp_per_floor", CardCrawlGame.metricData.max_hp_per_floor);
        addData("gold_per_floor", CardCrawlGame.metricData.gold_per_floor);
        addData("path_per_floor", CardCrawlGame.metricData.path_per_floor);
        addData("path_taken", CardCrawlGame.metricData.path_taken);
        addData("items_purchased", CardCrawlGame.metricData.items_purchased);
        addData("item_purchase_floors", CardCrawlGame.metricData.item_purchase_floors);
        addData("items_purged", CardCrawlGame.metricData.items_purged);
        addData("items_purged_floors", CardCrawlGame.metricData.items_purged_floors);
        addData("character_chosen", AbstractDungeon.player.chosenClass.name());
        addData("card_choices", CardCrawlGame.metricData.card_choices);
        addData("event_choices", CardCrawlGame.metricData.event_choices);
        addData("boss_relics", CardCrawlGame.metricData.boss_relics);
        addData("damage_taken", CardCrawlGame.metricData.damage_taken);
        addData("potions_obtained", CardCrawlGame.metricData.potions_obtained);
        addData("relics_obtained", CardCrawlGame.metricData.relics_obtained);
        addData("campfire_choices", CardCrawlGame.metricData.campfire_choices);

        addData("circlet_count", Integer.valueOf(AbstractDungeon.player.getCircletCount()));

        Prefs pref = AbstractDungeon.player.getPrefs();

        int numVictory = pref.getInteger("WIN_COUNT", 0);
        int numDeath = pref.getInteger("LOSE_COUNT", 0);

        if (numVictory <= 0) {
            addData("win_rate", Float.valueOf(0.0F));
        } else {
            addData("win_rate", Integer.valueOf(numVictory / (numDeath + numVictory)));
        }

        if (death && monsters != null) {
            addData("killed_by", AbstractDungeon.lastCombatMetricKey);
        } else {
            addData("killed_by", null);
        }
    }

    private void gatherAllDataAndSend(boolean death, boolean trueVictor, MonsterGroup monsters) {
        if (DeathScreen.shouldUploadMetricData()) {
            gatherAllData(death, trueVictor, monsters);
            sendPost(null);
        }
    }
    public void gatherAllDataAndSave(boolean death, boolean trueVictor, MonsterGroup monsters) {
        FileHandle file;
        gatherAllData(death, trueVictor, monsters);

        String data = this.gson.toJson(this.params);

        if (!Settings.isDailyRun) {
            String local_runs_save_path = "runs" + File.separator;

            switch (CardCrawlGame.saveSlot) {
                case 0:
                    break;

                default:
                    local_runs_save_path = local_runs_save_path + CardCrawlGame.saveSlot + "_";
                    break;
            }

            local_runs_save_path = local_runs_save_path + AbstractDungeon.player.chosenClass.name() + File.separator
                    + this.lastPlaytimeEnd + ".run";

            file = Gdx.files.local(local_runs_save_path);
        } else {
            String tmpPath = "runs" + File.separator;

            switch (CardCrawlGame.saveSlot) {
                case 0:
                    break;

                default:
                    tmpPath = tmpPath + CardCrawlGame.saveSlot + "_";
                    break;
            }

            file = Gdx.files.local(tmpPath + "DAILY" + File.separator + this.lastPlaytimeEnd + ".run");
        }

        file.writeString(data, false);
        removeExcessRunFiles();
    }

    private void removeExcessRunFiles() {
        if (!Settings.isConsoleBuild) {
            return;
        }

        FileHandle fh = Gdx.files.local("runs");
        FileHandle[] allFolders = fh.list();
        HashMap<String, FileHandle> map = new HashMap<>();
        List<String> runNames = new ArrayList<>();

        for (FileHandle fileHandle : allFolders) {
            FileHandle[] runs = fileHandle.list("run");
            for (FileHandle j : runs) {
                runNames.add(j.name());
                map.put(j.name(), j);
            }
        }

        int excessFileThreshold = 500;
        int numFilesToDelete = runNames.size() - excessFileThreshold;

        if (runNames.size() < excessFileThreshold) {
            return;
        }

        Collections.sort(runNames);

        for (int i = 0; i < numFilesToDelete; i++) {
            if (map.containsKey(runNames.get(i))) {
                logger.info("DELETING EXCESS RUN: " + map.get(((String) runNames.get(i)).toString()));
                ((FileHandle) map.get(runNames.get(i))).delete();
            }
        }
    }

    public void run() {
        switch (this.type) {
            case UPLOAD_CRASH:
                if (!Settings.isModded) {
                    gatherAllDataAndSend(this.death, false, this.monsters);
                }
                return;
            case UPLOAD_METRICS:
                if (!Settings.isModded) {
                    gatherAllDataAndSend(this.death, this.trueVictory, this.monsters);
                }
                return;
        }
        logger.info("Unspecified MetricRequestType: " + this.type.name() + " in run()");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\metrics\Metrics.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

