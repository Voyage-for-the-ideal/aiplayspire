package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.daily.mods.*;
import com.megacrit.cardcrawl.metrics.BotDataUploader;

import java.util.*;

public class ModHelper {
    private static HashMap<String, AbstractDailyMod> starterMods = new HashMap<>();
    private static HashMap<String, AbstractDailyMod> genericMods = new HashMap<>();
    private static HashMap<String, AbstractDailyMod> difficultyMods = new HashMap<>();

    private static HashMap<String, AbstractDailyMod> legacyMods = new HashMap<>();
    public static ArrayList<AbstractDailyMod> enabledMods = new ArrayList<>();

    public static void initialize() {
        addStarterMod((AbstractDailyMod) new Shiny());
        addStarterMod((AbstractDailyMod) new Allstar());
        addStarterMod((AbstractDailyMod) new Draft());
        addStarterMod((AbstractDailyMod) new SealedDeck());
        addStarterMod((AbstractDailyMod) new Insanity());
        addStarterMod((AbstractDailyMod) new Heirloom());
        addStarterMod((AbstractDailyMod) new Specialized());
        addStarterMod((AbstractDailyMod) new Chimera());
        addStarterMod((AbstractDailyMod) new CursedRun());

        addGenericMod((AbstractDailyMod) new Diverse());
        addGenericMod((AbstractDailyMod) new RedCards());
        addGenericMod((AbstractDailyMod) new GreenCards());
        addGenericMod((AbstractDailyMod) new BlueCards());
        addGenericMod((AbstractDailyMod) new PurpleCards());
        addGenericMod((AbstractDailyMod) new ColorlessCards());
        addGenericMod((AbstractDailyMod) new TimeDilation());
        addGenericMod((AbstractDailyMod) new Vintage());
        addGenericMod((AbstractDailyMod) new Hoarder());
        addGenericMod((AbstractDailyMod) new Flight());
        addGenericMod((AbstractDailyMod) new CertainFuture());
        addGenericMod((AbstractDailyMod) new ControlledChaos());

        addDifficultyMod((AbstractDailyMod) new BigGameHunter());
        addDifficultyMod((AbstractDailyMod) new Lethality());
        addDifficultyMod((AbstractDailyMod) new NightTerrors());
        addDifficultyMod((AbstractDailyMod) new Binary());
        addDifficultyMod((AbstractDailyMod) new Midas());
        addDifficultyMod((AbstractDailyMod) new Terminal());
        addDifficultyMod((AbstractDailyMod) new DeadlyEvents());

        addLegacyMod((AbstractDailyMod) new Brewmaster());
        addLegacyMod((AbstractDailyMod) new Colossus());
    }

    private static void addStarterMod(AbstractDailyMod mod) {
        starterMods.put(mod.modID, mod);
    }

    private static void addGenericMod(AbstractDailyMod mod) {
        genericMods.put(mod.modID, mod);
    }

    private static void addDifficultyMod(AbstractDailyMod mod) {
        difficultyMods.put(mod.modID, mod);
    }

    private static void addLegacyMod(AbstractDailyMod mod) {
        legacyMods.put(mod.modID, mod);
    }

    public static void setMods(List<String> modIDs) {
        setModsFalse();

        for (String m : modIDs) {
            if (!m.equals("Endless")) {
                enabledMods.add(getMod(m));
            }
        }
    }

    public static AbstractDailyMod getMod(String key) {
        AbstractDailyMod mod = starterMods.get(key);
        if (mod == null) {
            mod = genericMods.get(key);
        }
        if (mod == null) {
            mod = difficultyMods.get(key);
        }
        if (mod == null) {
            mod = legacyMods.get(key);
        }
        return mod;
    }

    public static ArrayList<String> getEnabledModIDs() {
        ArrayList<String> enabled = new ArrayList<>();
        for (AbstractDailyMod m : enabledMods) {
            if (m != null) {
                enabled.add(m.modID);
            }
        }
        return enabled;
    }

    private static void setTheMods(HashMap<String, AbstractDailyMod> modMap, long daysSince1970,
            AbstractPlayer.PlayerClass characterClass) {
        ArrayList<AbstractDailyMod> shuffledList = new ArrayList<>();

        for (Map.Entry<String, AbstractDailyMod> m : modMap.entrySet()) {

            if (((AbstractDailyMod) m.getValue()).classToExclude != characterClass) {
                shuffledList.add(m.getValue());
            }
        }

        int rotationConstant = 5;

        int modSelectionIndex = (int) (daysSince1970 % rotationConstant);

        if (modSelectionIndex < 0) {
            modSelectionIndex += rotationConstant;
        }

        int shuffleInterval = (int) (daysSince1970 / rotationConstant);
        Collections.shuffle(shuffledList, new Random(shuffleInterval));

        enabledMods.add(shuffledList.get(modSelectionIndex));
    }

    public static void setTodaysMods(long daysSince1970, AbstractPlayer.PlayerClass chosenClass) {
        setModsFalse();
        setTheMods(starterMods, daysSince1970, chosenClass);
        setTheMods(genericMods, daysSince1970, chosenClass);
        setTheMods(difficultyMods, daysSince1970, chosenClass);
    }

    public static boolean isModEnabled(String modID) {
        for (AbstractDailyMod m : enabledMods) {
            if (m != null && m.modID != null && m.modID.equals(modID)) {
                return true;
            }
        }
        return false;
    }

    public static void setModsFalse() {
        enabledMods.clear();
    }

    public static void uploadModData() {
        ArrayList<String> data = new ArrayList<>();

        for (Map.Entry<String, AbstractDailyMod> m : starterMods.entrySet()) {
            data.add(((AbstractDailyMod) m.getValue()).gameDataUploadData());
        }

        for (Map.Entry<String, AbstractDailyMod> m : genericMods.entrySet()) {
            data.add(((AbstractDailyMod) m.getValue()).gameDataUploadData());
        }

        for (Map.Entry<String, AbstractDailyMod> m : difficultyMods.entrySet()) {
            data.add(((AbstractDailyMod) m.getValue()).gameDataUploadData());
        }

        BotDataUploader.uploadDataAsync(BotDataUploader.GameDataType.DAILY_MOD_DATA,
                AbstractDailyMod.gameDataUploadHeader(), data);
    }

    public static void clearNulls() {
        for (Iterator<AbstractDailyMod> m = enabledMods.iterator(); m.hasNext();) {
            AbstractDailyMod derp = m.next();
            if (derp == null)
                m.remove();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\ModHelper.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

