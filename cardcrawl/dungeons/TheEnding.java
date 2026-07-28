package com.megacrit.cardcrawl.dungeons;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapGenerator;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.scenes.AbstractScene;
import com.megacrit.cardcrawl.scenes.TheEndingScene;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class TheEnding
        extends AbstractDungeon {
    private static final Logger logger = LogManager.getLogger(TheEnding.class.getName());
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("TheEnding");
    public static final String[] TEXT = uiStrings.TEXT;

    public static final String NAME = TEXT[0];
    public static final String ID = "TheEnding";

    public TheEnding(AbstractPlayer p, ArrayList<String> theList) {
        super(NAME, "TheEnding", p, theList);

        if (scene != null) {
            scene.dispose();
        }

        scene = (AbstractScene) new TheEndingScene();
        fadeColor = Color.valueOf("140a1eff");
        sourceFadeColor = Color.valueOf("140a1eff");

        initializeLevelSpecificChances();
        mapRng = new Random(Long.valueOf(Settings.seed.longValue() + (AbstractDungeon.actNum * 300)));
        generateSpecialMap();
        CardCrawlGame.music.changeBGM(id);
    }

    public TheEnding(AbstractPlayer p, SaveFile saveFile) {
        super(NAME, p, saveFile);
        CardCrawlGame.dungeon = this;

        if (scene != null) {
            scene.dispose();
        }

        scene = (AbstractScene) new TheEndingScene();
        fadeColor = Color.valueOf("140a1eff");
        sourceFadeColor = Color.valueOf("140a1eff");

        initializeLevelSpecificChances();
        miscRng = new Random(Long.valueOf(Settings.seed.longValue() + saveFile.floor_num));
        CardCrawlGame.music.changeBGM(id);
        mapRng = new Random(Long.valueOf(Settings.seed.longValue() + (saveFile.act_num * 300)));
        generateSpecialMap();
        firstRoomChosen = true;
        populatePathTaken(saveFile);
    }

    private void generateSpecialMap() {
        long startTime = System.currentTimeMillis();

        map = new ArrayList<>();

        ArrayList<MapRoomNode> row1 = new ArrayList<>();
        MapRoomNode restNode = new MapRoomNode(3, 0);
        restNode.room = (AbstractRoom) new RestRoom();
        MapRoomNode shopNode = new MapRoomNode(3, 1);
        shopNode.room = (AbstractRoom) new ShopRoom();
        MapRoomNode enemyNode = new MapRoomNode(3, 2);
        enemyNode.room = (AbstractRoom) new MonsterRoomElite();
        MapRoomNode bossNode = new MapRoomNode(3, 3);
        bossNode.room = (AbstractRoom) new MonsterRoomBoss();
        MapRoomNode victoryNode = new MapRoomNode(3, 4);
        victoryNode.room = (AbstractRoom) new TrueVictoryRoom();

        connectNode(restNode, shopNode);
        connectNode(shopNode, enemyNode);
        enemyNode.addEdge(new MapEdge(enemyNode.x, enemyNode.y, enemyNode.offsetX, enemyNode.offsetY, bossNode.x,
                bossNode.y, bossNode.offsetX, bossNode.offsetY, false));

        row1.add(new MapRoomNode(0, 0));
        row1.add(new MapRoomNode(1, 0));
        row1.add(new MapRoomNode(2, 0));
        row1.add(restNode);
        row1.add(new MapRoomNode(4, 0));
        row1.add(new MapRoomNode(5, 0));
        row1.add(new MapRoomNode(6, 0));

        ArrayList<MapRoomNode> row2 = new ArrayList<>();
        row2.add(new MapRoomNode(0, 1));
        row2.add(new MapRoomNode(1, 1));
        row2.add(new MapRoomNode(2, 1));
        row2.add(shopNode);
        row2.add(new MapRoomNode(4, 1));
        row2.add(new MapRoomNode(5, 1));
        row2.add(new MapRoomNode(6, 1));

        ArrayList<MapRoomNode> row3 = new ArrayList<>();
        row3.add(new MapRoomNode(0, 2));
        row3.add(new MapRoomNode(1, 2));
        row3.add(new MapRoomNode(2, 2));
        row3.add(enemyNode);
        row3.add(new MapRoomNode(4, 2));
        row3.add(new MapRoomNode(5, 2));
        row3.add(new MapRoomNode(6, 2));

        ArrayList<MapRoomNode> row4 = new ArrayList<>();
        row4.add(new MapRoomNode(0, 3));
        row4.add(new MapRoomNode(1, 3));
        row4.add(new MapRoomNode(2, 3));
        row4.add(bossNode);
        row4.add(new MapRoomNode(4, 3));
        row4.add(new MapRoomNode(5, 3));
        row4.add(new MapRoomNode(6, 3));

        ArrayList<MapRoomNode> row5 = new ArrayList<>();
        row5.add(new MapRoomNode(0, 4));
        row5.add(new MapRoomNode(1, 4));
        row5.add(new MapRoomNode(2, 4));
        row5.add(victoryNode);
        row5.add(new MapRoomNode(4, 4));
        row5.add(new MapRoomNode(5, 4));
        row5.add(new MapRoomNode(6, 4));

        map.add(row1);
        map.add(row2);
        map.add(row3);
        map.add(row4);
        map.add(row5);

        logger.info("Generated the following dungeon map:");
        logger.info(MapGenerator.toString(map, Boolean.valueOf(true)));
        logger.info("Game Seed: " + Settings.seed);
        logger.info("Map generation time: " + (System.currentTimeMillis() - startTime) + "ms");
        firstRoomChosen = false;
        fadeIn();
    }

    private void connectNode(MapRoomNode src, MapRoomNode dst) {
        src.addEdge(new MapEdge(src.x, src.y, src.offsetX, src.offsetY, dst.x, dst.y, dst.offsetX, dst.offsetY, false));
    }

    protected void initializeLevelSpecificChances() {
        shopRoomChance = 0.05F;
        restRoomChance = 0.12F;
        treasureRoomChance = 0.0F;
        eventRoomChance = 0.22F;
        eliteRoomChance = 0.08F;

        smallChestChance = 0;
        mediumChestChance = 100;
        largeChestChance = 0;

        commonRelicChance = 0;
        uncommonRelicChance = 100;
        rareRelicChance = 0;

        colorlessRareChance = 0.3F;
        if (AbstractDungeon.ascensionLevel >= 12) {
            cardUpgradedChance = 0.25F;
        } else {
            cardUpgradedChance = 0.5F;
        }
    }

    protected void generateMonsters() {
        monsterList = new ArrayList<>();
        monsterList.add("Shield and Spear");
        monsterList.add("Shield and Spear");
        monsterList.add("Shield and Spear");

        eliteMonsterList = new ArrayList<>();
        eliteMonsterList.add("Shield and Spear");
        eliteMonsterList.add("Shield and Spear");
        eliteMonsterList.add("Shield and Spear");
    }

    protected void generateWeakEnemies(int count) {
    }

    protected void generateStrongEnemies(int count) {
    }

    protected void generateElites(int count) {
    }

    protected ArrayList<String> generateExclusions() {
        return new ArrayList<>();
    }

    protected void initializeBoss() {
        bossList.add("The Heart");
        bossList.add("The Heart");
        bossList.add("The Heart");
    }

    protected void initializeEventList() {
    }

    protected void initializeEventImg() {
        if (eventBackgroundImg != null) {
            eventBackgroundImg.dispose();
            eventBackgroundImg = null;
        }
        eventBackgroundImg = ImageMaster.loadImage("images/ui/event/panel.png");
    }

    protected void initializeShrineList() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\dungeons\TheEnding
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

