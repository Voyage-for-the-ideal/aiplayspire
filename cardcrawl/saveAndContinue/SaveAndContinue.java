package com.megacrit.cardcrawl.saveAndContinue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.ExceptionHandler;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.exceptions.SaveFileLoadError;
import com.megacrit.cardcrawl.helpers.AsyncSaver;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.relics.BottledFlame;
import com.megacrit.cardcrawl.relics.BottledLightning;
import com.megacrit.cardcrawl.relics.BottledTornado;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;

public class SaveAndContinue {
    private static final Logger logger = LogManager.getLogger(SaveAndContinue.class.getName());
    public static final String SAVE_PATH = "saves" + File.separator;

    public static String getPlayerSavePath(AbstractPlayer.PlayerClass c) {
        StringBuilder sb = new StringBuilder();
        sb.append(SAVE_PATH);

        if (CardCrawlGame.saveSlot != 0) {
            sb.append(CardCrawlGame.saveSlot).append("_");
        }

        sb.append(c.name()).append(".autosave");
        return sb.toString();
    }

    public static boolean saveExistsAndNotCorrupted(AbstractPlayer p) {
        String filepath = getPlayerSavePath(p.chosenClass);
        boolean fileExists = Gdx.files.local(filepath).exists();

        if (fileExists) {
            try {
                loadSaveFile(filepath);
            } catch (SaveFileLoadError saveFileLoadError) {
                deleteSave(p);
                logger.info(p.chosenClass.name() + " save INVALID!");
                return false;
            }
            logger.info(p.chosenClass.name() + " save exists and is valid.");
            return true;
        }
        logger.info(p.chosenClass.name() + " save does NOT exist!");
        return false;
    }

    public static String loadSaveString(AbstractPlayer.PlayerClass c) {
        return loadSaveString(getPlayerSavePath(c));
    }

    private static String loadSaveString(String filePath) {
        FileHandle file = Gdx.files.local(filePath);
        String data = file.readString();

        if (SaveFileObfuscator.isObfuscated(data)) {
            return SaveFileObfuscator.decode(data, "key");
        }
        return data;
    }

    public static SaveFile loadSaveFile(AbstractPlayer.PlayerClass c) {
        String fileName = getPlayerSavePath(c);
        try {
            return loadSaveFile(fileName);
        } catch (SaveFileLoadError e) {
            logger.info("Exception occurred while loading save!");
            ExceptionHandler.handleException((Exception) e, logger);

            Gdx.app.exit();

            return null;
        }
    }

    private static SaveFile loadSaveFile(String filePath) throws SaveFileLoadError {
        SaveFile saveFile = null;
        Gson gson = new Gson();
        String savestr = null;
        Exception err = null;
        try {
            savestr = loadSaveString(filePath);
            saveFile = (SaveFile) gson.fromJson(savestr, SaveFile.class);
        } catch (Exception e) {

            if (Gdx.files.local(filePath).exists()) {
                SaveHelper.preserveCorruptFile(filePath);
            }
            err = e;
            if (!filePath.endsWith(".backUp")) {
                logger.info(filePath + " was corrupt, loading backup...");
                return loadSaveFile(filePath + ".backUp");
            }
        }
        if (saveFile == null) {
            throw new SaveFileLoadError("Unable to load save file: " + filePath, err);
        }
        logger.info(filePath + " save file was successfully loaded.");
        return saveFile;
    }

    public static void save(SaveFile save) {
        CardCrawlGame.loadingSave = false;
        HashMap<Object, Object> params = new HashMap<>();

        params.put("name", save.name);
        params.put("loadout", save.loadout);
        params.put("current_health", Integer.valueOf(save.current_health));
        params.put("max_health", Integer.valueOf(save.max_health));
        params.put("max_orbs", Integer.valueOf(save.max_orbs));
        params.put("gold", Integer.valueOf(save.gold));
        params.put("hand_size", Integer.valueOf(save.hand_size));
        params.put("red", Integer.valueOf(save.red));
        params.put("green", Integer.valueOf(save.green));
        params.put("blue", Integer.valueOf(save.blue));

        params.put("monsters_killed", Integer.valueOf(save.monsters_killed));
        params.put("elites1_killed", Integer.valueOf(save.elites1_killed));
        params.put("elites2_killed", Integer.valueOf(save.elites2_killed));
        params.put("elites3_killed", Integer.valueOf(save.elites3_killed));
        params.put("gold_gained", Integer.valueOf(save.gold_gained));
        params.put("mystery_machine", Integer.valueOf(save.mystery_machine));
        params.put("champions", Integer.valueOf(save.champions));
        params.put("perfect", Integer.valueOf(save.perfect));
        params.put("overkill", Boolean.valueOf(save.overkill));
        params.put("combo", Boolean.valueOf(save.combo));

        params.put("cards", save.cards);
        params.put("obtained_cards", save.obtained_cards);
        params.put("relics", save.relics);
        params.put("relic_counters", save.relic_counters);
        params.put("potions", save.potions);
        params.put("potion_slots", Integer.valueOf(save.potion_slots));

        params.put("is_endless_mode", Boolean.valueOf(save.is_endless_mode));
        params.put("blights", save.blights);
        params.put("blight_counters", save.blight_counters);
        params.put("endless_increments", save.endless_increments);

        params.put("chose_neow_reward", Boolean.valueOf(save.chose_neow_reward));
        params.put("neow_bonus", save.neow_bonus);
        params.put("neow_cost", save.neow_cost);
        params.put("is_ascension_mode", Boolean.valueOf(save.is_ascension_mode));
        params.put("ascension_level", Integer.valueOf(save.ascension_level));
        params.put("level_name", save.level_name);
        params.put("floor_num", Integer.valueOf(save.floor_num));
        params.put("act_num", Integer.valueOf(save.act_num));
        params.put("event_list", save.event_list);
        params.put("one_time_event_list", save.one_time_event_list);
        params.put("potion_chance", Integer.valueOf(save.potion_chance));
        params.put("event_chances", save.event_chances);
        params.put("monster_list", save.monster_list);
        params.put("elite_monster_list", save.elite_monster_list);
        params.put("boss_list", save.boss_list);
        params.put("play_time", Long.valueOf(save.play_time));
        params.put("save_date", Long.valueOf(save.save_date));
        params.put("seed", Long.valueOf(save.seed));
        params.put("special_seed", Long.valueOf(save.special_seed));
        params.put("seed_set", Boolean.valueOf(save.seed_set));
        params.put("is_daily", Boolean.valueOf(save.is_daily));
        params.put("is_final_act_on", Boolean.valueOf(save.is_final_act_on));
        params.put("has_ruby_key", Boolean.valueOf(save.has_ruby_key));
        params.put("has_emerald_key", Boolean.valueOf(save.has_emerald_key));
        params.put("has_sapphire_key", Boolean.valueOf(save.has_sapphire_key));
        params.put("daily_date", Long.valueOf(save.daily_date));
        params.put("is_trial", Boolean.valueOf(save.is_trial));
        params.put("daily_mods", save.daily_mods);
        params.put("custom_mods", save.custom_mods);
        params.put("boss", save.boss);
        params.put("purgeCost", Integer.valueOf(save.purgeCost));
        params.put("monster_seed_count", Integer.valueOf(save.monster_seed_count));
        params.put("event_seed_count", Integer.valueOf(save.event_seed_count));
        params.put("merchant_seed_count", Integer.valueOf(save.merchant_seed_count));
        params.put("card_seed_count", Integer.valueOf(save.card_seed_count));
        params.put("treasure_seed_count", Integer.valueOf(save.treasure_seed_count));
        params.put("relic_seed_count", Integer.valueOf(save.relic_seed_count));
        params.put("potion_seed_count", Integer.valueOf(save.potion_seed_count));
        params.put("ai_seed_count", Integer.valueOf(save.ai_seed_count));
        params.put("shuffle_seed_count", Integer.valueOf(save.shuffle_seed_count));
        params.put("card_random_seed_count", Integer.valueOf(save.card_random_seed_count));
        params.put("card_random_seed_randomizer", Integer.valueOf(save.card_random_seed_randomizer));
        params.put("path_x", save.path_x);
        params.put("path_y", save.path_y);
        params.put("room_x", Integer.valueOf(save.room_x));
        params.put("room_y", Integer.valueOf(save.room_y));
        params.put("spirit_count", Integer.valueOf(save.spirit_count));
        params.put("current_room", save.current_room);
        params.put("common_relics", save.common_relics);
        params.put("uncommon_relics", save.uncommon_relics);
        params.put("rare_relics", save.rare_relics);
        params.put("shop_relics", save.shop_relics);
        params.put("boss_relics", save.boss_relics);

        params.put("post_combat", Boolean.valueOf(save.post_combat));
        params.put("mugged", Boolean.valueOf(save.mugged));
        params.put("smoked", Boolean.valueOf(save.smoked));
        params.put("combat_rewards", save.combat_rewards);

        if (AbstractDungeon.player.hasRelic("Bottled Flame")) {
            saveBottle(params, "Bottled Flame", "bottled_flame", ((BottledFlame) AbstractDungeon.player

                    .getRelic("Bottled Flame")).card);
        } else {
            params.put("bottled_flame", null);
        }

        if (AbstractDungeon.player.hasRelic("Bottled Lightning")) {
            saveBottle(params, "Bottled Lightning", "bottled_lightning", ((BottledLightning) AbstractDungeon.player

                    .getRelic("Bottled Lightning")).card);
        } else {
            params.put("bottled_lightning", null);
        }

        if (AbstractDungeon.player.hasRelic("Bottled Tornado")) {
            saveBottle(params, "Bottled Tornado", "bottled_tornado", ((BottledTornado) AbstractDungeon.player

                    .getRelic("Bottled Tornado")).card);
        } else {
            params.put("bottled_tornado", null);
        }

        params.put("metric_campfire_rested", Integer.valueOf(save.metric_campfire_rested));
        params.put("metric_campfire_upgraded", Integer.valueOf(save.metric_campfire_upgraded));
        params.put("metric_campfire_rituals", Integer.valueOf(save.metric_campfire_rituals));
        params.put("metric_campfire_meditates", Integer.valueOf(save.metric_campfire_meditates));
        params.put("metric_purchased_purges", Integer.valueOf(save.metric_purchased_purges));
        params.put("metric_potions_floor_spawned", save.metric_potions_floor_spawned);
        params.put("metric_potions_floor_usage", save.metric_potions_floor_usage);
        params.put("metric_current_hp_per_floor", save.metric_current_hp_per_floor);
        params.put("metric_max_hp_per_floor", save.metric_max_hp_per_floor);
        params.put("metric_gold_per_floor", save.metric_gold_per_floor);
        params.put("metric_path_per_floor", save.metric_path_per_floor);
        params.put("metric_path_taken", save.metric_path_taken);
        params.put("metric_items_purchased", save.metric_items_purchased);
        params.put("metric_item_purchase_floors", save.metric_item_purchase_floors);
        params.put("metric_items_purged", save.metric_items_purged);
        params.put("metric_items_purged_floors", save.metric_items_purged_floors);
        params.put("metric_card_choices", save.metric_card_choices);
        params.put("metric_event_choices", save.metric_event_choices);
        params.put("metric_boss_relics", save.metric_boss_relics);
        params.put("metric_damage_taken", save.metric_damage_taken);
        params.put("metric_potions_obtained", save.metric_potions_obtained);
        params.put("metric_relics_obtained", save.metric_relics_obtained);
        params.put("metric_campfire_choices", save.metric_campfire_choices);
        params.put("metric_build_version", save.metric_build_version);
        params.put("metric_seed_played", save.metric_seed_played);
        params.put("metric_floor_reached", Integer.valueOf(save.metric_floor_reached));
        params.put("metric_playtime", Long.valueOf(save.metric_playtime));

        Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
        String data = gson.toJson(params);
        String filepath = getPlayerSavePath(AbstractDungeon.player.chosenClass);

        if (Settings.isBeta) {
            AsyncSaver.save(filepath + "BETA", data);
        }

        AsyncSaver.save(filepath, SaveFileObfuscator.encode(data, "key"));
    }

    private static void saveBottle(HashMap<Object, Object> params, String bottleId, String save_name,
            AbstractCard card) {
        if (AbstractDungeon.player.hasRelic(bottleId)) {
            if (card != null) {
                params.put(save_name, card.cardID);
                params.put(save_name + "_upgrade", Integer.valueOf(card.timesUpgraded));
                params.put(save_name + "_misc", Integer.valueOf(card.misc));
            } else {
                params.put(save_name, null);
            }
        } else {
            params.put(save_name, null);
        }
    }

    public static void deleteSave(AbstractPlayer p) {
        String savePath = p.getSaveFilePath();
        logger.info("DELETING " + savePath + " SAVE");
        Gdx.files.local(savePath).delete();
        Gdx.files.local(savePath + ".backUp").delete();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\saveAndContinue\
 * SaveAndContinue.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

