package battleaimod;

import basemod.BaseMod;
import basemod.ReflectionHacks;
import basemod.TopPanelItem;
import basemod.eventUtil.EventUtils;
import basemod.interfaces.*;
import basemod.patches.com.megacrit.cardcrawl.helpers.PotionLibrary.PotionHelperGetPotion;
import battleaimod.battleai.BattleAiController;
import battleaimod.battleai.CommandRunnerController;
import battleaimod.battleai.playorder.*;
import battleaimod.networking.AiClient;
import battleaimod.networking.AiServer;
import battleaimod.networking.BattleClientController;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.ui.ModSelectWindow;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.ExhaustAction;
import com.megacrit.cardcrawl.actions.unique.DualWieldAction;
import com.megacrit.cardcrawl.actions.unique.NightmareAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.audio.MainMusic;
import com.megacrit.cardcrawl.audio.Sfx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.colorless.Forethought;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.monsters.exordium.Lagavulin;
import com.megacrit.cardcrawl.monsters.exordium.SlimeBoss;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.GamblingChip;
import com.megacrit.cardcrawl.relics.MummifiedHand;
import com.megacrit.cardcrawl.relics.TheSpecimen;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EmptyRoom;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.ThoughtBubble;
import com.megacrit.cardcrawl.vfx.campfire.CampfireSmithEffect;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.GridSelectConfrimCommand;
import ludicrousspeed.simulator.commands.HandSelectCommand;
import ludicrousspeed.simulator.commands.HandSelectConfirmCommand;
import org.lwjgl.opengl.Display;
import savestate.PotionState;
import savestate.SaveState;
import savestate.SaveStateMod;
import savestate.fastobjects.ScreenShakeFast;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Function;

import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.actionManager;
import static ludicrousspeed.LudicrousSpeedMod.controller;
import static ludicrousspeed.LudicrousSpeedMod.plaidMode;

@SpireInitializer
public class BattleAiMod implements PostInitializeSubscriber, PostUpdateSubscriber, OnStartBattleSubscriber, PreUpdateSubscriber, RenderSubscriber {
    public static final HashMap<AbstractPlayer.PlayerClass, String> MESSAGE_WORDS = new HashMap<AbstractPlayer.PlayerClass, String>() {{
        put(AbstractPlayer.PlayerClass.IRONCLAD, "Strategizing");
        put(AbstractPlayer.PlayerClass.THE_SILENT, "Scheming");
        put(AbstractPlayer.PlayerClass.WATCHER, "Foreseeing");
        put(AbstractPlayer.PlayerClass.DEFECT, "Processing");
    }};

    public final static long MESSAGE_TIME_MILLIS = 1500L;
    public static String steveMessage = null;
    public static AiServer aiServer = null;
    public static AiClient aiClient = null;
    public static boolean shouldStartAiFromServer = false;
    public static BattleAiController battleAiController = null;

    public static CommandRunnerController rerunController = null;

    public static SaveState saveState;
    public static int requestedTurnNum;
    public static boolean goFast = false;
    public static boolean shouldStartClient = false;
    public static boolean autoStartAi = false;

    public static boolean isServer;
    public static boolean isClient;

    public static BattleClientController clientController;
    public static BattleClientController.ControllerMode battleClientControllerMode;

    public static ArrayList<Comparator<AbstractCard>> cardPlayHeuristics = new ArrayList<>();
    public static HashMap<Class, Comparator<AbstractCard>> actionHeuristics = new HashMap<>();

    public static ArrayList<Function<SaveState, Integer>> additionalValueFunctions = new ArrayList<>();

    public static SpireConfig optionsConfig;

    public BattleAiMod() {
        BaseMod.subscribe(this);
        BaseMod.subscribe(new LudicrousSpeedMod());

        try {
            optionsConfig = new SpireConfig("BattleAIMod", "options");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Shut off the MTS console window, It increasingly slows things down
        ModSelectWindow window = ReflectionHacks.getPrivateStatic(Loader.class, "ex");
        window.removeAll();

        CardCrawlGame.screenShake = new ScreenShakeFast();

        cardPlayHeuristics.add(IronCladPlayOrder.COMPARATOR);
        cardPlayHeuristics.add(DefectPlayOrder.COMPARATOR);
        cardPlayHeuristics.add(SilentPlayOrder.COMPARATOR);

        actionHeuristics.put(DiscardAction.class, DiscardOrder.COMPARATOR);
        actionHeuristics.put(ExhaustAction.class, ExhaustOrder.COMPARATOR);

        actionHeuristics.put(NightmareAction.class, new BadCardsLastHeuristic());
        actionHeuristics.put(DualWieldAction.class, new BadCardsLastHeuristic());
    }

    public static void sendGameState() {
        if (battleAiController != null) {
            battleAiController.step();

            if (battleAiController.isDone()) {
                battleAiController = null;
            }
        }
    }

    public static void initialize() {
        BattleAiMod mod = new BattleAiMod();
    }

    @Override
    public void receivePostInitialize() {
        // Sometimes doesn't come back to hand for some reason
        CardLibrary.cards.remove(Forethought.ID);

        // Current behavior would make this a chat opti on, it won't be interesting out of the box
        HashMap<String, AbstractRelic> sharedRelics = ReflectionHacks
                .getPrivateStatic(RelicLibrary.class, "sharedRelics");
        sharedRelics.remove(GamblingChip.ID);

        Iterator<String> actualPotions = PotionHelper.potions.iterator();
        while (actualPotions.hasNext()) {
            String potionId = actualPotions.next();
            for (String toRemove : PotionState.UNPLAYABLE_POTIONS) {
                if (potionId.equals(toRemove)) {
                    actualPotions.remove();
                    continue;
                }
            }
        }

        String isServerFlag = System.getProperty("isServer");
        String isPlaidModeFlag = System.getProperty("isPlaidMode");


        if (isServerFlag != null) {
            if (Boolean.parseBoolean(isServerFlag)) {
                BattleAiMod.isServer = true;
            }
        }

        if (isPlaidModeFlag != null) {
            plaidMode = true;
        }

        String isClientFlag = System.getProperty("isClient");

        if (isClientFlag != null) {
            if (Boolean.parseBoolean(isClientFlag)) {
                BattleAiMod.isClient = true;
            }
        }

        CardCrawlGame.displayVersion = false;
        ReflectionHacks.setPrivateStaticFinal(MummifiedHand.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(BaseMod.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(TheSpecimen.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(AbstractDungeon.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(Lagavulin.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(SlimeBoss.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(CardGroup.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(CardHelper.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(UnlockTracker.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(ImageMaster.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(AbstractMonster.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(MainMusic.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(AbstractPlayer.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(Sfx.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(PotionHelper.class, "logger", new SilentLogger());
        ReflectionHacks
                .setPrivateStaticFinal(PotionHelperGetPotion.class, "logger", new SilentLogger());


        ReflectionHacks.setPrivateStaticFinal(EventUtils.class, "eventLogger", new SilentLogger());

        if (plaidMode) {
            Settings.MASTER_VOLUME = 0;
            goFast = true;
            SaveStateMod.shouldGoFast = true;

            Settings.ACTION_DUR_XFAST = 0.001F;
            Settings.ACTION_DUR_FASTER = 0.002F;
            Settings.ACTION_DUR_FAST = 0.0025F;
            Settings.ACTION_DUR_MED = 0.005F;
            Settings.ACTION_DUR_LONG = .01F;
            Settings.ACTION_DUR_XLONG = .015F;

            if (isServer) {
                if (aiServer == null) {
                    aiServer = new AiServer();
                }
            }
        }

        CardCrawlGame.sound.update();
        clientController = new BattleClientController();
        battleClientControllerMode = BattleClientController.getModeOption();
        autoStartAi = BattleClientController.getAutoStartAi();

        BaseMod.addTopPanelItem(new StartAiClientTopPanel());
        BaseMod.registerModBadge(ImageMaster
                .loadImage("Icon.png"), "Battle Ai Mod", "Board Engineer", "Plays the Battle for yourself", new BattleAiModOptionsPanel());

    }

    @Override
    public void receivePostUpdate() {
        if (steveMessage != null) {
            String messageToDisplay = String.format(" %s... NL %s", MESSAGE_WORDS
                    .getOrDefault(AbstractDungeon.player.chosenClass, "Processing"), steveMessage);
            steveMessage = null;

            AbstractDungeon.effectList
                    .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, (float) MESSAGE_TIME_MILLIS / 1000.F, messageToDisplay, true));

        }

        if (battleAiController == null && shouldStartAiFromServer) {
            shouldStartAiFromServer = false;
            controller = battleAiController = new BattleAiController(saveState, requestedTurnNum);
        }

        clientController.update();
    }

    public class StartAiClientTopPanel extends TopPanelItem {
        public static final String ID = "battleaimod:startclient";

        public StartAiClientTopPanel() {
            super(new Texture("img/StartSteve.png"), ID);
        }

        @Override
        protected void onClick() {
            if (!isEnabled()) {
                return;
            }


            if (aiClient == null) {
                try {
                    aiClient = new AiClient();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (aiClient != null) {
                aiClient.sendState();
            }
        }

        @Override
        public void update() {
            if (isEnabled()) {
                super.update();
            }
        }

        @Override
        public void render(SpriteBatch sb) {
            if (isEnabled()) {
                super.render(sb);
            }
        }

        private boolean isEnabled() {
            return BattleAiMod.battleClientControllerMode == BattleClientController.ControllerMode.TOP_PANEL_LAUNCHER;
        }
    }

    @Override
    public void receiveOnBattleStart(AbstractRoom abstractRoom) {
        if (autoStartAi) {
            shouldStartClient = true;
        }
    }

    @Override
    public void receivePreUpdate() {
        if (battleAiController == null && shouldStartAiFromServer) {
            shouldStartAiFromServer = false;
            battleAiController = new BattleAiController(saveState, requestedTurnNum);
            controller = battleAiController;
        }

        if (actionManager.actions.isEmpty() && actionManager.currentAction == null) {
            if (shouldStartClient) {
                shouldStartClient = false;
                AbstractDungeon.effectList
                        .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 2.0F, "Hello World", true));

                actionManager.actions.add(new WaitAction(2.0F));
                actionManager.actions.add(new AbstractGameAction() {
                    @Override
                    public void update() {
                        AbstractDungeon.effectList
                                .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 3.0F, "Here we go", true));

                        if (BattleAiMod.aiClient == null) {
                            try {
                                BattleAiMod.aiClient = new AiClient();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        isDone = true;

                        if (BattleAiMod.aiClient != null) {
                            BattleAiMod.aiClient.sendState();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void receiveRender(SpriteBatch spriteBatch) {
        clientController.render(spriteBatch);
    }


    // Patch Communication Mod to include the reroll option and process it when selected
    @SpirePatch(clz = Display.class, method = "update", paramtypez = {boolean.class})
    public static class DoUpdatesHappenPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> logType(boolean processMessages) {
            if (plaidMode) {
                return SpireReturn.Return(null);
            } else {
                return SpireReturn.Continue();
            }
        }
    }



    // Fade out the main menu screen instantly as its just an artificial wait
    @SpirePatch(clz = MainMenuScreen.class, method = "fadeOut")
    public static class StartProfilingFadeOutPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> logType(MainMenuScreen screen) {
            if (screen.isFadingOut) {
                Color overlayColor = ReflectionHacks.getPrivate(screen, MainMenuScreen.class, "overlayColor");
                overlayColor.a = 1.0F;
                ReflectionHacks.setPrivate(screen, MainMenuScreen.class, "overlayColor", overlayColor);
            }

            return SpireReturn.Continue();
        }
    }

    // Battle fadout
    @SpirePatch(clz = AbstractRoom.class, method = "update")
    public static class EndBattleFadeOutPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> logType(AbstractRoom room) {
            if (room.isBattleOver) {
                ReflectionHacks.setPrivate(room, AbstractRoom.class, "endBattleTimer", -.1F);
            }

            if (AbstractRoom.waitTimer > 0.0F) {
                AbstractRoom.waitTimer = Gdx.graphics.getDeltaTime() / 2.0F;
            }

            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = MapRoomNode.class, method = "update")
    public static class MapMousePatch {
        @SpirePrefixPatch
        public static void fastMouse(MapRoomNode mapRoomNode) {
            float animWaitTimer = ReflectionHacks.getPrivate(mapRoomNode, MapRoomNode.class, "animWaitTimer");
            if (animWaitTimer > 0.0) {
                ReflectionHacks.setPrivate(mapRoomNode, MapRoomNode.class, "animWaitTimer", -.1F);
            }
        }
    }

    static long campfireUpdatesInvoked = 0;
    static boolean waitOnOpen = false;

    @SpirePatch(clz = CampfireSmithEffect.class, method = SpirePatch.CONSTRUCTOR)
    public static class FastSmithConstructorPatch {
        @SpirePrefixPatch
        public static void fastSmith(CampfireSmithEffect effect) {
            campfireUpdatesInvoked = 0;
            waitOnOpen = false;
        }
    }

    @SpirePatch(clz = CampfireSmithEffect.class, method = "update")
    public static class FastSmithPatch {
        @SpirePrefixPatch
        public static void fastSmith(CampfireSmithEffect effect) {
            if (effect.duration > 0.0F) {
                boolean openedScreen = ReflectionHacks.getPrivate(effect, CampfireSmithEffect.class, "openedScreen");
                if (!waitOnOpen && !AbstractDungeon.isScreenUp) {
                    if (campfireUpdatesInvoked == 10) {
                        waitOnOpen = true;
                    } else if (campfireUpdatesInvoked > 10) {
                        if (openedScreen) {
                            effect.duration = Gdx.graphics.getDeltaTime() / 2.0F;
                        }
                    }
                }

            }

            campfireUpdatesInvoked++;
        }
    }

    @SpirePatch(clz = GridCardSelectScreen.class, method = "open", paramtypez = {CardGroup.class, int.class, String.class, boolean.class, boolean.class, boolean.class, boolean.class})
    public static class GridOpenPeekPatch {
        @SpirePostfixPatch
        public static void PeekThing(GridCardSelectScreen screen, CardGroup group, int numCards, String tipMsg, boolean forUpgrade, boolean forTransform, boolean canCancel, boolean forPurge) {
            waitOnOpen = false;
        }
    }


    @SpirePatch(clz = CardCrawlGame.class, method = "create")
    public static class GameStartupPatch {
        @SpirePostfixPatch
        public static void afterStart(CardCrawlGame game) {
            if (isServer) {
                System.err.println("Skipping Splash Screen for Char Select");

                // Sets the current dungeon
                Settings.seed = 123L;
                AbstractDungeon.generateSeeds();

                // TODO this needs to be the actual character class or bad things happen
                new Exordium(CardCrawlGame.characterManager
                        .getCharacter(AbstractPlayer.PlayerClass.IRONCLAD), new ArrayList<>());

                AbstractDungeon.currMapNode.room = new EmptyRoom();

                CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;
            }
        }
    }


    @SpirePatch(
            clz = DeathScreen.class,
            paramtypez = {MonsterGroup.class},
            method = SpirePatch.CONSTRUCTOR
    )
    public static class DisableDeathScreenpatch {
        public static SpireReturn Prefix(DeathScreen _instance, MonsterGroup monsterGroup) {
            if (isServer) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = DeathScreen.class,
            paramtypez = {},
            method = "reopen"
    )
    public static class DisableDeathReopenScreenpatch {
        public static SpireReturn Prefix(DeathScreen _instance) {
            if (isServer) {
                AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.DEATH;
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = SaveFile.class,
            paramtypez = {SaveFile.SaveType.class},
            method = SpirePatch.CONSTRUCTOR
    )
    public static class NoMakeSavePatch {
        public static SpireReturn Prefix(SaveFile _instance, SaveFile.SaveType type) {
            if (isServer) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = SaveAndContinue.class,
            paramtypez = {SaveFile.class},
            method = "save"
    )
    public static class NoSavingPatch {
        public static SpireReturn Prefix(SaveFile save) {
            if (isServer) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

}
