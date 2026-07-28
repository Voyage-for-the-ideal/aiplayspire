package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.integrations.DistributorFactory;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.screens.mainMenu.SaveSlotScreen;
import com.megacrit.cardcrawl.vfx.GameSavedEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SaveHelper {
    private static final Logger logger = LogManager.getLogger(SaveHelper.class.getName());

    public static void initialize() {
    }

    private static Boolean isGog() {
        return Boolean.valueOf((CardCrawlGame.publisherIntegration.getType() == DistributorFactory.Distributor.GOG));
    }

    private static String getSaveDir() {
        if (Settings.isBeta || isGog().booleanValue()) {
            return "betaPreferences";
        }
        return "preferences";
    }

    public static boolean doesPrefExist(String name) {
        return Gdx.files.local(getSaveDir() + File.separator + name).exists();
    }

    public static void deletePrefs(int slot) {
        String dir = getSaveDir() + File.separator;

        deleteFile(dir + slotName("STSDataVagabond", slot));
        deleteFile(dir + slotName("STSDataTheSilent", slot));
        deleteFile(dir + slotName("STSDataDefect", slot));
        deleteFile(dir + slotName("STSDataWatcher", slot));

        deleteFile(dir + slotName("STSAchievements", slot));
        deleteFile(dir + slotName("STSDaily", slot));
        deleteFile(dir + slotName("STSSeenBosses", slot));
        deleteFile(dir + slotName("STSSeenCards", slot));
        deleteFile(dir + slotName("STSBetaCardPreference", slot));
        deleteFile(dir + slotName("STSSeenRelics", slot));
        deleteFile(dir + slotName("STSUnlockProgress", slot));
        deleteFile(dir + slotName("STSUnlocks", slot));

        deleteFile(dir + slotName("STSGameplaySettings", slot));
        deleteFile(dir + slotName("STSInputSettings", slot));
        deleteFile(dir + slotName("STSInputSettings_Controller", slot));
        deleteFile(dir + slotName("STSSound", slot));

        deleteFile(dir + slotName("STSPlayer", slot));
        deleteFile(dir + slotName("STSTips", slot));

        dir = "runs" + File.separator;
        deleteFolder(dir + slotName("IRONCLAD", slot));
        deleteFolder(dir + slotName("THE_SILENT", slot));
        deleteFolder(dir + slotName("DEFECT", slot));
        deleteFolder(dir + slotName("WATCHER", slot));
        deleteFolder(dir + slotName("DAILY", slot));

        dir = "saves" + File.separator;
        deleteFile(dir + slotName("IRONCLAD.autosave", slot));
        deleteFile(dir + slotName("DEFECT.autosave", slot));
        deleteFile(dir + slotName("THE_SILENT.autosave", slot));
        deleteFile(dir + slotName("WATCHER.autosave", slot));
        deleteFile(dir + slotName("IRONCLAD.autosave.backUp", slot));
        deleteFile(dir + slotName("DEFECT.autosave.backUp", slot));
        deleteFile(dir + slotName("THE_SILENT.autosave.backUp", slot));
        deleteFile(dir + slotName("WATCHER.autosave.backUp", slot));

        if (Settings.isBeta || isGog().booleanValue()) {
            deleteFile(dir + slotName("IRONCLAD.autosaveBETA", slot));
            deleteFile(dir + slotName("DEFECT.autosaveBETA", slot));
            deleteFile(dir + slotName("THE_SILENT.autosaveBETA", slot));
            deleteFile(dir + slotName("WATCHER.autosaveBETA", slot));
            deleteFile(dir + slotName("IRONCLAD.autosaveBETA.backUp", slot));
            deleteFile(dir + slotName("DEFECT.autosaveBETA.backUp", slot));
            deleteFile(dir + slotName("THE_SILENT.autosaveBETA.backUp", slot));
            deleteFile(dir + slotName("WATCHER.autosaveBETA.backUp", slot));
        }

        CardCrawlGame.saveSlotPref.putString(slotName("PROFILE_NAME", slot), "");
        CardCrawlGame.saveSlotPref.putFloat(slotName("COMPLETION", slot), 0.0F);
        CardCrawlGame.saveSlotPref.putLong(slotName("PLAYTIME", slot), 0L);
        CardCrawlGame.saveSlotPref.flush();

        if (slot == CardCrawlGame.saveSlot || CardCrawlGame.saveSlot == -1) {
            String name = "";
            boolean newDefaultSet = false;

            for (int i = 0; i < 3; i++) {
                name = CardCrawlGame.saveSlotPref.getString(slotName("PROFILE_NAME", i), "");
                if (!name.equals("")) {
                    logger.info("Current slot deleted, DEFAULT_SLOT is now " + i);
                    CardCrawlGame.saveSlotPref.putInteger("DEFAULT_SLOT", i);
                    newDefaultSet = true;
                    SaveSlotScreen.slotDeleted = true;

                    break;
                }
            }

            if (!newDefaultSet) {
                logger.info("All slots deleted, DEFAULT_SLOT is now -1");
                CardCrawlGame.saveSlotPref.putInteger("DEFAULT_SLOT", -1);
            }

            CardCrawlGame.saveSlotPref.flush();
        }
    }

    private static void deleteFile(String fileName) {
        logger.info("Deleting " + fileName);
        if (Gdx.files.local(fileName).delete()) {
            logger.info(fileName + " deleted.");
        }

        if (Gdx.files.local(fileName + ".backUp").delete()) {
            logger.info(fileName + ".backUp deleted.");
        }
    }

    private static void deleteFolder(String dirName) {
        logger.info("Deleting " + dirName);
        if (Gdx.files.local(dirName).deleteDirectory()) {
            logger.info(dirName + " deleted.");
        }
    }

    public static String slotName(String name, int slot) {
        switch (slot) {

            case 0:
                return name;
        }
        name = slot + "_" + name;

        return name;
    }
    public static Prefs getPrefs(String name) {
        switch (CardCrawlGame.saveSlot) {
            case 0:
                break;

            default:
                name = CardCrawlGame.saveSlot + "_" + name;
                break;
        }

        Gson gson = new Gson();
        Prefs retVal = new Prefs();

        Type type = (new TypeToken<Map<String, String>>() {
        }).getType();

        String filepath = getSaveDir() + File.separator + name;

        String jsonStr = null;
        try {
            jsonStr = loadJson(filepath);
            if (jsonStr.isEmpty()) {
                logger.error("Empty Pref file: name=" + name + ", filepath=" + filepath);
                handleCorruption(jsonStr, filepath, name);
                retVal = getPrefs(name);
            } else {
                retVal.data = (Map<String, String>) gson.fromJson(jsonStr, type);
            }
        } catch (JsonSyntaxException e) {
            logger.error("Corrupt Pref file", (Throwable) e);
            handleCorruption(jsonStr, filepath, name);
            retVal = getPrefs(name);
        }
        retVal.filepath = filepath;
        return retVal;
    }

    private static void handleCorruption(String jsonStr, String filepath, String name) {
        preserveCorruptFile(filepath);
        FileHandle backup = Gdx.files.local(filepath + ".backUp");
        if (backup.exists()) {
            backup.moveTo(Gdx.files.local(filepath));
            logger.info("Original corrupted, backup loaded for " + filepath);
        }
    }

    public static void preserveCorruptFile(String filePath) {
        FileHandle file = Gdx.files.local(filePath);
        FileHandle corruptFile = Gdx.files.local("sendToDevs" + File.separator + filePath + ".corrupt");
        file.moveTo(corruptFile);
    }

    private static String loadJson(String filepath) {
        if (Gdx.files.local(filepath).exists()) {
            return Gdx.files.local(filepath).readString(String.valueOf(StandardCharsets.UTF_8));
        }
        Map<String, String> map = new HashMap<>();
        Gson gson = new Gson();
        AsyncSaver.save(filepath, gson.toJson(map));
        return "{}";
    }

    public static boolean saveExists() {
        StringBuilder retVal = new StringBuilder();

        retVal.append(getSaveDir()).append(File.separator);

        switch (CardCrawlGame.saveSlot) {
            case 0:
                retVal.append("STSPlayer");

                return Gdx.files.local(retVal.toString()).exists();
            case 1:
            case 2:
            case 3:
                retVal.append(CardCrawlGame.saveSlot).append("_STSPlayer");
                return Gdx.files.local(retVal.toString()).exists();
        }
        retVal.append("STSPlayer");
        return Gdx.files.local(retVal.toString()).exists();
    }

    public static void saveIfAppropriate(SaveFile.SaveType saveType) {
        if (!shouldSave()) {
            return;
        }
        SaveFile saveFile = new SaveFile(saveType);
        SaveAndContinue.save(saveFile);
        AbstractDungeon.effectList.add(new GameSavedEffect());
    }

    public static boolean shouldSave() {
        if (AbstractDungeon.nextRoom != null
                && AbstractDungeon.nextRoom.getRoom() instanceof com.megacrit.cardcrawl.rooms.TrueVictoryRoom) {
            return false;
        }
        return !Settings.isDemo;
    }

    public static boolean shouldDeleteSave() {
        return !Settings.isDemo;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\SaveHelper
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

