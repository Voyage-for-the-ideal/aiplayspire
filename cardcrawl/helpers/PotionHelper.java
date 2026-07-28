package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.metrics.BotDataUploader;
import com.megacrit.cardcrawl.potions.*;
import com.megacrit.cardcrawl.random.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;

public class PotionHelper {
    private static final Logger logger = LogManager.getLogger(PotionHelper.class.getName());
    public static ArrayList<String> potions = new ArrayList<>();

    public static int POTION_COMMON_CHANCE = 65;
    public static int POTION_UNCOMMON_CHANCE = 25;

    public static void initialize(AbstractPlayer.PlayerClass chosenClass) {
        potions.clear();
        potions = getPotions(chosenClass, false);
    }

    public static ArrayList<AbstractPotion> getPotionsByRarity(AbstractPotion.PotionRarity rarity) {
        ArrayList<AbstractPotion> retVal = new ArrayList<>();

        for (String s : getPotions(null, true)) {
            AbstractPotion p = getPotion(s);
            if (p.rarity == rarity) {
                retVal.add(p);
            }
        }

        return retVal;
    }

    public static ArrayList<String> getPotions(AbstractPlayer.PlayerClass c, boolean getAll) {
        ArrayList<String> retVal = new ArrayList<>();

        if (!getAll) {
            switch (c) {
                case IRONCLAD:
                    retVal.add("BloodPotion");
                    retVal.add("ElixirPotion");
                    retVal.add("HeartOfIron");
                    break;
                case THE_SILENT:
                    retVal.add("Poison Potion");
                    retVal.add("CunningPotion");
                    retVal.add("GhostInAJar");
                    break;
                case DEFECT:
                    retVal.add("FocusPotion");
                    retVal.add("PotionOfCapacity");
                    retVal.add("EssenceOfDarkness");
                    break;
                case WATCHER:
                    retVal.add("BottledMiracle");
                    retVal.add("StancePotion");
                    retVal.add("Ambrosia");
                    break;
            }

        } else {
            retVal.add("BloodPotion");
            retVal.add("ElixirPotion");
            retVal.add("HeartOfIron");
            retVal.add("Poison Potion");
            retVal.add("CunningPotion");
            retVal.add("GhostInAJar");
            retVal.add("FocusPotion");
            retVal.add("PotionOfCapacity");
            retVal.add("EssenceOfDarkness");
            retVal.add("BottledMiracle");
            retVal.add("StancePotion");
            retVal.add("Ambrosia");
        }

        retVal.add("Block Potion");
        retVal.add("Dexterity Potion");
        retVal.add("Energy Potion");
        retVal.add("Explosive Potion");
        retVal.add("Fire Potion");
        retVal.add("Strength Potion");
        retVal.add("Swift Potion");
        retVal.add("Weak Potion");
        retVal.add("FearPotion");
        retVal.add("AttackPotion");
        retVal.add("SkillPotion");
        retVal.add("PowerPotion");
        retVal.add("ColorlessPotion");
        retVal.add("SteroidPotion");
        retVal.add("SpeedPotion");
        retVal.add("BlessingOfTheForge");

        retVal.add("Regen Potion");
        retVal.add("Ancient Potion");
        retVal.add("LiquidBronze");
        retVal.add("GamblersBrew");
        retVal.add("EssenceOfSteel");
        retVal.add("DuplicationPotion");
        retVal.add("DistilledChaos");
        retVal.add("LiquidMemories");

        retVal.add("CultistPotion");
        retVal.add("Fruit Juice");
        retVal.add("SneckoOil");
        retVal.add("FairyPotion");
        retVal.add("SmokeBomb");
        retVal.add("EntropicBrew");

        return retVal;
    }

    public static AbstractPotion getRandomPotion(Random rng) {
        String randomKey = potions.get(rng.random(potions.size() - 1));
        return getPotion(randomKey);
    }

    public static AbstractPotion getRandomPotion() {
        String randomKey = potions.get(AbstractDungeon.potionRng.random(potions.size() - 1));
        return getPotion(randomKey);
    }

    public static boolean isAPotion(String key) {
        return getPotions(null, true).contains(key);
    }

    public static AbstractPotion getPotion(String name) {
        if (name == null || name.equals("")) {
            return null;
        }

        switch (name) {
            case "Ambrosia":
                return (AbstractPotion) new Ambrosia();
            case "BottledMiracle":
                return (AbstractPotion) new BottledMiracle();
            case "EssenceOfDarkness":
                return (AbstractPotion) new EssenceOfDarkness();
            case "Block Potion":
                return (AbstractPotion) new BlockPotion();
            case "Dexterity Potion":
                return (AbstractPotion) new DexterityPotion();
            case "Energy Potion":
                return (AbstractPotion) new EnergyPotion();
            case "Explosive Potion":
                return (AbstractPotion) new ExplosivePotion();
            case "Fire Potion":
                return (AbstractPotion) new FirePotion();
            case "Strength Potion":
                return (AbstractPotion) new StrengthPotion();
            case "Swift Potion":
                return (AbstractPotion) new SwiftPotion();
            case "Poison Potion":
                return (AbstractPotion) new PoisonPotion();
            case "Weak Potion":
                return (AbstractPotion) new WeakenPotion();
            case "FearPotion":
                return (AbstractPotion) new FearPotion();
            case "SkillPotion":
                return (AbstractPotion) new SkillPotion();
            case "PowerPotion":
                return (AbstractPotion) new PowerPotion();
            case "AttackPotion":
                return (AbstractPotion) new AttackPotion();
            case "ColorlessPotion":
                return (AbstractPotion) new ColorlessPotion();
            case "SteroidPotion":
                return (AbstractPotion) new SteroidPotion();
            case "SpeedPotion":
                return (AbstractPotion) new SpeedPotion();
            case "BlessingOfTheForge":
                return (AbstractPotion) new BlessingOfTheForge();

            case "PotionOfCapacity":
                return (AbstractPotion) new PotionOfCapacity();
            case "CunningPotion":
                return (AbstractPotion) new CunningPotion();
            case "DistilledChaos":
                return (AbstractPotion) new DistilledChaosPotion();
            case "Ancient Potion":
                return (AbstractPotion) new AncientPotion();
            case "Regen Potion":
                return (AbstractPotion) new RegenPotion();
            case "GhostInAJar":
                return (AbstractPotion) new GhostInAJar();
            case "FocusPotion":
                return (AbstractPotion) new FocusPotion();
            case "LiquidBronze":
                return (AbstractPotion) new LiquidBronze();
            case "LiquidMemories":
                return (AbstractPotion) new LiquidMemories();
            case "GamblersBrew":
                return (AbstractPotion) new GamblersBrew();
            case "EssenceOfSteel":
                return (AbstractPotion) new EssenceOfSteel();
            case "BloodPotion":
                return (AbstractPotion) new BloodPotion();
            case "StancePotion":
                return (AbstractPotion) new StancePotion();
            case "DuplicationPotion":
                return (AbstractPotion) new DuplicationPotion();
            case "ElixirPotion":
                return (AbstractPotion) new Elixir();

            case "CultistPotion":
                return (AbstractPotion) new CultistPotion();
            case "Fruit Juice":
                return (AbstractPotion) new FruitJuice();
            case "SneckoOil":
                return (AbstractPotion) new SneckoOil();
            case "FairyPotion":
                return (AbstractPotion) new FairyPotion();
            case "SmokeBomb":
                return (AbstractPotion) new SmokeBomb();
            case "EntropicBrew":
                return (AbstractPotion) new EntropicBrew();
            case "HeartOfIron":
                return (AbstractPotion) new HeartOfIron();

            case "Potion Slot":
                return null;
        }
        logger.info("MISSING KEY: POTIONHELPER 37: " + name);
        return (AbstractPotion) new FirePotion();
    }

    public static void uploadPotionData() {
        initialize(AbstractPlayer.PlayerClass.IRONCLAD);
        HashSet<String> ironcladPotions = new HashSet<>(potions);
        HashSet<String> sharedPotions = new HashSet<>(potions);

        initialize(AbstractPlayer.PlayerClass.THE_SILENT);
        HashSet<String> silentPotions = new HashSet<>(potions);
        sharedPotions.retainAll(potions);

        initialize(AbstractPlayer.PlayerClass.DEFECT);
        HashSet<String> defectPotions = new HashSet<>(potions);
        sharedPotions.retainAll(potions);

        initialize(AbstractPlayer.PlayerClass.WATCHER);
        HashSet<String> watcherPotions = new HashSet<>(potions);
        sharedPotions.retainAll(potions);

        ironcladPotions.removeAll(sharedPotions);
        silentPotions.removeAll(sharedPotions);
        defectPotions.removeAll(sharedPotions);
        watcherPotions.removeAll(sharedPotions);

        potions.clear();

        ArrayList<String> data = new ArrayList<>();
        for (String id : ironcladPotions) {
            data.add(getPotion(id).getUploadData("RED"));
        }
        for (String id : silentPotions) {
            data.add(getPotion(id).getUploadData("GREEN"));
        }
        for (String id : defectPotions) {
            data.add(getPotion(id).getUploadData("BLUE"));
        }
        for (String id : watcherPotions) {
            data.add(getPotion(id).getUploadData("PURPLE"));
        }
        for (String id : sharedPotions) {
            data.add(getPotion(id).getUploadData("ALL"));
        }
        BotDataUploader.uploadDataAsync(BotDataUploader.GameDataType.POTION_DATA, AbstractPotion.gameDataUploadHeader(),
                data);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * PotionHelper.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

