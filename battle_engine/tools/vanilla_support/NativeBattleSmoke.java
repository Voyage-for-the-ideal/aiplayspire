package aiplayspire.offline;

import battleaimod.networking.BattleClientController;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.*;
import com.megacrit.cardcrawl.rooms.EmptyRoom;
import savestate.SaveState;
import java.util.ArrayList;

/** Test JAR only: drive the same toggle used by Start Steve from a saved real battle. */
@SpirePatch(clz=CardCrawlGame.class, method="update")
public class NativeBattleSmoke {
    private static boolean restored, started;
    private static int stoppedFrames;
    private static boolean takeoverDone;
    private static SelectorSmoke selector;
    @SpirePostfixPatch public static void after() throws Exception {
        if (started && Boolean.getBoolean("aiplayspire.testTakeover") && !takeoverDone) {
            if (stoppedFrames == 0 && battleaimod.networking.AiClient.active()
                    && java.nio.file.Files.exists(java.nio.file.Paths.get(System.getProperty("battleai.trace")))) {
                BattleClientController.toggle();
                stoppedFrames++;
            } else if (stoppedFrames > 0) {
                if (battleaimod.networking.AiClient.active() || battleaimod.BattleAiMod.rerunController != null)
                    throw new IllegalStateException("Stopped planner applied a late action");
                if (++stoppedFrames >= 100 && BattleClientController.canSendState()) {
                    java.nio.file.Files.createFile(java.nio.file.Paths.get("takeover-verified"));
                    takeoverDone=true;
                    BattleClientController.toggle();
                }
            }
        }
        if (started || CardCrawlGame.characterManager == null || CardCrawlGame.mainMenuScreen == null) return;
        String source=System.getProperty("aiplayspire.smokeSnapshot");
        if (source==null) return;
        if (!restored) {
            restored=true;
            SaveState state=SaveState.forFile(source);
            Settings.seed=state.rngState.seed; AbstractDungeon.generateSeeds();
            new Exordium(CardCrawlGame.characterManager.getCharacter(state.playerState.chosenClass),new ArrayList<>());
            AbstractDungeon.currMapNode.room=new EmptyRoom();
            state.initPlayerAndCardPool(); state.loadState();
            // A restored room has no act-intro labels. Remove only the synthetic
            // dungeon constructor's title overlay, not game actions or combat effects.
            AbstractDungeon.effectList.removeIf(e -> e instanceof com.megacrit.cardcrawl.vfx.scene.LevelTransitionTextOverlayEffect);
            AbstractDungeon.effectsQueue.removeIf(e -> e instanceof com.megacrit.cardcrawl.vfx.scene.LevelTransitionTextOverlayEffect);
            AbstractDungeon.topLevelEffects.removeIf(e -> e instanceof com.megacrit.cardcrawl.vfx.scene.LevelTransitionTextOverlayEffect);
            AbstractDungeon.topLevelEffectsQueue.removeIf(e -> e instanceof com.megacrit.cardcrawl.vfx.scene.LevelTransitionTextOverlayEffect);
            CardCrawlGame.mode=CardCrawlGame.GameMode.GAMEPLAY;
            return;
        }
        String selectorCase=System.getProperty("aiplayspire.selectorCase");
        if (selectorCase!=null) {
            if (selector==null) selector=new SelectorSmoke(selectorCase);
            selector.tick();
            return;
        }
        if (BattleClientController.canSendState()) {
            started=true;
            if(Boolean.getBoolean("aiplayspire.smokeAuto"))
                battleaimod.BattleAiMod.shouldStartClient=battleaimod.BattleAiMod.autoStartAi;
            else BattleClientController.toggle();
        }
    }
}
