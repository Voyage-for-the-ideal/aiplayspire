package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.beyond.*;
import com.megacrit.cardcrawl.events.city.*;
import com.megacrit.cardcrawl.events.exordium.*;
import com.megacrit.cardcrawl.events.shrines.*;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;

public class EventHelper {
    private static final Logger logger = LogManager.getLogger(EventHelper.class.getName());

    private static final float BASE_ELITE_CHANCE = 0.1F;

    private static final float BASE_MONSTER_CHANCE = 0.1F;

    private static final float BASE_SHOP_CHANCE = 0.03F;

    private static final float BASE_TREASURE_CHANCE = 0.02F;

    private static final float RAMP_ELITE_CHANCE = 0.1F;

    private static final float RAMP_MONSTER_CHANCE = 0.1F;

    private static final float RAMP_SHOP_CHANCE = 0.03F;

    private static final float RAMP_TREASURE_CHANCE = 0.02F;

    private static final float RESET_ELITE_CHANCE = 0.0F;
    private static final float RESET_MONSTER_CHANCE = 0.1F;
    private static final float RESET_SHOP_CHANCE = 0.03F;
    private static final float RESET_TREASURE_CHANCE = 0.02F;
    private static float ELITE_CHANCE = 0.1F;
    private static float MONSTER_CHANCE = 0.1F;
    private static float SHOP_CHANCE = 0.03F;
    public static float TREASURE_CHANCE = 0.02F;
    private static ArrayList<Float> saveFilePreviousChances;
    private static String saveFileLastEventChoice;

    public enum RoomResult {
        EVENT, ELITE, TREASURE, SHOP, MONSTER;
    }

    public static RoomResult roll() {
        return roll(AbstractDungeon.eventRng);
    }

    public static RoomResult roll(Random eventRng) {
        saveFilePreviousChances = getChances();
        float roll = eventRng.random();
        logger.info("Rolling for room type... EVENT_RNG_COUNTER: " + AbstractDungeon.eventRng.counter);

        boolean forceChest = false;
        if (AbstractDungeon.player.hasRelic("Tiny Chest")) {
            AbstractRelic r = AbstractDungeon.player.getRelic("Tiny Chest");
            r.counter++;
            if (r.counter == 4) {
                r.counter = 0;
                r.flash();
                forceChest = true;
            }
        }

        logger.info("ROLL: " + roll);
        logger.info("ELIT: " + ELITE_CHANCE);
        logger.info("MNST: " + MONSTER_CHANCE);
        logger.info("SHOP: " + SHOP_CHANCE);
        logger.info("TRSR: " + TREASURE_CHANCE);

        int eliteSize = 0;
        if (ModHelper.isModEnabled("DeadlyEvents")) {
            eliteSize = (int) (ELITE_CHANCE * 100.0F);
        }
        if (AbstractDungeon.floorNum < 6) {
            eliteSize = 0;
        }
        int monsterSize = (int) (MONSTER_CHANCE * 100.0F);
        int shopSize = (int) (SHOP_CHANCE * 100.0F);
        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom) {
            shopSize = 0;
        }
        int treasureSize = (int) (TREASURE_CHANCE * 100.0F);

        int fillIndex = 0;

        RoomResult[] possibleResults = new RoomResult[100];
        Arrays.fill((Object[]) possibleResults, RoomResult.EVENT);

        if (ModHelper.isModEnabled("DeadlyEvents")) {

            Arrays.fill((Object[]) possibleResults,

                    Math.min(99, fillIndex),
                    Math.min(100, fillIndex + eliteSize), RoomResult.ELITE);

            fillIndex += eliteSize;
            Arrays.fill((Object[]) possibleResults,

                    Math.min(99, fillIndex),
                    Math.min(100, fillIndex + eliteSize), RoomResult.ELITE);

            fillIndex += eliteSize;
        }

        Arrays.fill((Object[]) possibleResults,

                Math.min(99, fillIndex),
                Math.min(100, fillIndex + monsterSize), RoomResult.MONSTER);

        fillIndex += monsterSize;

        Arrays.fill((Object[]) possibleResults, Math.min(99, fillIndex), Math.min(100, fillIndex + shopSize),
                RoomResult.SHOP);
        fillIndex += shopSize;

        Arrays.fill((Object[]) possibleResults,

                Math.min(99, fillIndex),
                Math.min(100, fillIndex + treasureSize), RoomResult.TREASURE);

        RoomResult choice = possibleResults[(int) (roll * 100.0F)];
        if (forceChest) {
            choice = RoomResult.TREASURE;
        }

        if (choice == RoomResult.ELITE) {
            ELITE_CHANCE = 0.0F;
            if (ModHelper.isModEnabled("DeadlyEvents")) {
                ELITE_CHANCE = 0.1F;
            }
        } else {
            ELITE_CHANCE += 0.1F;
        }

        if (choice == RoomResult.MONSTER) {
            if (AbstractDungeon.player.hasRelic("Juzu Bracelet")) {
                AbstractDungeon.player.getRelic("Juzu Bracelet").flash();
                choice = RoomResult.EVENT;
            }
            MONSTER_CHANCE = 0.1F;
        } else {
            MONSTER_CHANCE += 0.1F;
        }

        if (choice == RoomResult.SHOP) {
            SHOP_CHANCE = 0.03F;
        } else {
            SHOP_CHANCE += 0.03F;
        }

        if (Settings.isEndless && AbstractDungeon.player.hasBlight("MimicInfestation")) {
            if (choice == RoomResult.TREASURE) {
                if (AbstractDungeon.player.hasRelic("Juzu Bracelet")) {
                    AbstractDungeon.player.getRelic("Juzu Bracelet").flash();
                    choice = RoomResult.EVENT;
                } else {
                    choice = RoomResult.ELITE;
                }
                TREASURE_CHANCE = 0.02F;
                if (ModHelper.isModEnabled("DeadlyEvents")) {
                    TREASURE_CHANCE += 0.02F;
                }
            }

        } else if (choice == RoomResult.TREASURE) {
            TREASURE_CHANCE = 0.02F;
        } else {
            TREASURE_CHANCE += 0.02F;
            if (ModHelper.isModEnabled("DeadlyEvents")) {
                TREASURE_CHANCE += 0.02F;
            }
        }

        return choice;
    }

    public static void resetProbabilities() {
        saveFilePreviousChances = null;
        ELITE_CHANCE = 0.0F;
        MONSTER_CHANCE = 0.1F;
        SHOP_CHANCE = 0.03F;
        TREASURE_CHANCE = 0.02F;
    }

    public static void setChances(ArrayList<Float> chances) {
        ELITE_CHANCE = ((Float) chances.get(0)).floatValue();
        MONSTER_CHANCE = ((Float) chances.get(1)).floatValue();
        SHOP_CHANCE = ((Float) chances.get(2)).floatValue();
        TREASURE_CHANCE = ((Float) chances.get(3)).floatValue();
    }

    public static ArrayList<Float> getChances() {
        ArrayList<Float> chances = new ArrayList<>();
        chances.add(Float.valueOf(ELITE_CHANCE));
        chances.add(Float.valueOf(MONSTER_CHANCE));
        chances.add(Float.valueOf(SHOP_CHANCE));
        chances.add(Float.valueOf(TREASURE_CHANCE));
        return chances;
    }

    public static ArrayList<Float> getChancesPreRoll() {
        if (saveFilePreviousChances != null) {
            return saveFilePreviousChances;
        }

        return getChances();
    }

    public static String getMostRecentEventID() {
        return saveFileLastEventChoice;
    }

    public static AbstractEvent getEvent(String key) {
        if (Settings.isDev)
            ;

        saveFileLastEventChoice = key;
        switch (key) {

            case "Accursed Blacksmith":
                return (AbstractEvent) new AccursedBlacksmith();
            case "Bonfire Elementals":
                return (AbstractEvent) new Bonfire();
            case "Fountain of Cleansing":
                return (AbstractEvent) new FountainOfCurseRemoval();
            case "Designer":
                return (AbstractEvent) new Designer();
            case "Duplicator":
                return (AbstractEvent) new Duplicator();
            case "Lab":
                return (AbstractEvent) new Lab();
            case "Match and Keep!":
                return (AbstractEvent) new GremlinMatchGame();
            case "Golden Shrine":
                return (AbstractEvent) new GoldShrine();
            case "Purifier":
                return (AbstractEvent) new PurificationShrine();
            case "Transmorgrifier":
                return (AbstractEvent) new Transmogrifier();
            case "Wheel of Change":
                return (AbstractEvent) new GremlinWheelGame();
            case "Upgrade Shrine":
                return (AbstractEvent) new UpgradeShrine();
            case "FaceTrader":
                return (AbstractEvent) new FaceTrader();
            case "NoteForYourself":
                return (AbstractEvent) new NoteForYourself();
            case "WeMeetAgain":
                return (AbstractEvent) new WeMeetAgain();
            case "The Woman in Blue":
                return (AbstractEvent) new WomanInBlue();

            case "Big Fish":
                return (AbstractEvent) new BigFish();
            case "The Cleric":
                return (AbstractEvent) new Cleric();
            case "Dead Adventurer":
                return (AbstractEvent) new DeadAdventurer();
            case "Golden Wing":
                return (AbstractEvent) new GoldenWing();
            case "Golden Idol":
                return (AbstractEvent) new GoldenIdolEvent();
            case "World of Goop":
                return (AbstractEvent) new GoopPuddle();
            case "Forgotten Altar":
                return (AbstractEvent) new ForgottenAltar();
            case "Scrap Ooze":
                return (AbstractEvent) new ScrapOoze();
            case "Liars Game":
                return (AbstractEvent) new Sssserpent();
            case "Living Wall":
                return (AbstractEvent) new LivingWall();
            case "Mushrooms":
                return (AbstractEvent) new Mushrooms();
            case "N'loth":
                return (AbstractEvent) new Nloth();
            case "Shining Light":
                return (AbstractEvent) new ShiningLight();

            case "Vampires":
                return (AbstractEvent) new Vampires();
            case "Ghosts":
                return (AbstractEvent) new Ghosts();
            case "Addict":
                return (AbstractEvent) new Addict();
            case "Back to Basics":
                return (AbstractEvent) new BackToBasics();
            case "Beggar":
                return (AbstractEvent) new Beggar();
            case "Cursed Tome":
                return (AbstractEvent) new CursedTome();
            case "Drug Dealer":
                return (AbstractEvent) new DrugDealer();
            case "Knowing Skull":
                return (AbstractEvent) new KnowingSkull();
            case "Masked Bandits":
                return (AbstractEvent) new MaskedBandits();
            case "Nest":
                return (AbstractEvent) new Nest();
            case "The Library":
                return (AbstractEvent) new TheLibrary();
            case "The Mausoleum":
                return (AbstractEvent) new TheMausoleum();
            case "The Joust":
                return (AbstractEvent) new TheJoust();
            case "Colosseum":
                return (AbstractEvent) new Colosseum();

            case "Mysterious Sphere":
                return (AbstractEvent) new MysteriousSphere();
            case "SecretPortal":
                return (AbstractEvent) new SecretPortal();
            case "Tomb of Lord Red Mask":
                return (AbstractEvent) new TombRedMask();
            case "Falling":
                return (AbstractEvent) new Falling();
            case "Winding Halls":
                return (AbstractEvent) new WindingHalls();
            case "The Moai Head":
                return (AbstractEvent) new MoaiHead();
            case "SensoryStone":
                return (AbstractEvent) new SensoryStone();
            case "MindBloom":
                return (AbstractEvent) new MindBloom();
        }
        logger.info("---------------------------\nERROR: Unspecified key: " + key
                + " in EventHelper.\n---------------------------");

        return null;
    }

    public static String getEventName(String key) {
        switch (key) {

            case "Accursed Blacksmith":
                return AccursedBlacksmith.NAME;
            case "Bonfire Elementals":
                return Bonfire.NAME;
            case "Fountain of Cleansing":
                return FountainOfCurseRemoval.NAME;
            case "Designer":
                return Designer.NAME;
            case "Duplicator":
                return Duplicator.NAME;
            case "Lab":
                return Lab.NAME;
            case "Match and Keep!":
                return GremlinMatchGame.NAME;
            case "Golden Shrine":
                return GoldShrine.NAME;
            case "Purifier":
                return PurificationShrine.NAME;
            case "Transmorgrifier":
                return Transmogrifier.NAME;
            case "Wheel of Change":
                return GremlinWheelGame.NAME;
            case "Upgrade Shrine":
                return UpgradeShrine.NAME;
            case "FaceTrader":
                return FaceTrader.NAME;
            case "NoteForYourself":
                return NoteForYourself.NAME;
            case "WeMeetAgain":
                return WeMeetAgain.NAME;
            case "The Woman in Blue":
                return WomanInBlue.NAME;

            case "Big Fish":
                return BigFish.NAME;
            case "The Cleric":
                return Cleric.NAME;
            case "Dead Adventurer":
                return DeadAdventurer.NAME;
            case "Golden Wing":
                return GoldenWing.NAME;
            case "Golden Idol":
                return GoldenIdolEvent.NAME;
            case "World of Goop":
                return GoopPuddle.NAME;
            case "Forgotten Altar":
                return ForgottenAltar.NAME;
            case "Scrap Ooze":
                return ScrapOoze.NAME;
            case "Liars Game":
                return Sssserpent.NAME;
            case "Living Wall":
                return LivingWall.NAME;
            case "Mushrooms":
                return Mushrooms.NAME;
            case "N'loth":
                return Nloth.NAME;
            case "Shining Light":
                return ShiningLight.NAME;

            case "Vampires":
                return Vampires.NAME;
            case "Ghosts":
                return Ghosts.NAME;
            case "Addict":
                return Addict.NAME;
            case "Back to Basics":
                return BackToBasics.NAME;
            case "Beggar":
                return Beggar.NAME;
            case "Cursed Tome":
                return CursedTome.NAME;
            case "Drug Dealer":
                return DrugDealer.NAME;
            case "Knowing Skull":
                return KnowingSkull.NAME;
            case "Masked Bandits":
                return MaskedBandits.NAME;
            case "Nest":
                return Nest.NAME;
            case "The Library":
                return TheLibrary.NAME;
            case "The Mausoleum":
                return TheMausoleum.NAME;
            case "The Joust":
                return TheJoust.NAME;
            case "Colosseum":
                return Colosseum.NAME;

            case "Mysterious Sphere":
                return MysteriousSphere.NAME;
            case "SecretPortal":
                return SecretPortal.NAME;
            case "Tomb of Lord Red Mask":
                return TombRedMask.NAME;
            case "Falling":
                return Falling.NAME;
            case "Winding Halls":
                return WindingHalls.NAME;
            case "The Moai Head":
                return MoaiHead.NAME;
            case "SensoryStone":
                return SensoryStone.NAME;
            case "MindBloom":
                return MindBloom.NAME;
        }
        return "";
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * EventHelper.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

