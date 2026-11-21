package battleaimod.networking;

import basemod.ClickableUIElement;
import battleaimod.BattleAiMod;
import battleaimod.search.SearchProfile;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;
import ludicrousspeed.LudicrousSpeedMod;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class BattleClientController {
    private static final float X_POSITION = 10F * Settings.scale;
    private static final float Y_POSITION = (Settings.HEIGHT - Settings.HEIGHT / 5);

    private static final Texture START_BUTTON_TEXTURE = new Texture("img/StartSteve.png");

    private static final Texture CONTROLLER_BACKGROUND = new Texture("img/controllerbackground.png");

    private static final float BANNER_WIDTH = (float) (START_BUTTON_TEXTURE
            .getWidth()) * 4f * Settings.scale;

    StartUIButton startButton;
    public BattleClientController() {
        startButton = new StartUIButton();
    }

    public void update() {
        AiClient.tick(inCombat());
        if (isEnabled() && inCombat()) startButton.update();
    }

    public void render(SpriteBatch sb) {
        if (!LudicrousSpeedMod.plaidMode && isEnabled()) {
            if (inCombat()) {

                sb.setColor(Color.WHITE);

                // double the height for reasons?
                float height = (float) (START_BUTTON_TEXTURE.getHeight() * 1.2) * Settings.scale;
                float width = BANNER_WIDTH;

                float y = Y_POSITION - START_BUTTON_TEXTURE.getHeight() / 8.f;
                float x = X_POSITION;

                sb.draw(CONTROLLER_BACKGROUND, x, y, width, height);

                startButton.render(sb);
            }
        }
    }

    public static class StartUIButton extends ClickableUIElement {
        public StartUIButton() {
            super(START_BUTTON_TEXTURE);
            x = 10F * Settings.scale;
            y = Y_POSITION;
            hitbox = new Hitbox(x, y, START_BUTTON_TEXTURE.getWidth(), START_BUTTON_TEXTURE
                    .getHeight());
        }

        @Override
        protected void onHover() {
            this.angle = MathHelper.angleLerpSnap(this.angle, 15.0F);
            this.tint.a = 0.25F;
        }

        @Override
        protected void onUnhover() {
            this.angle = MathHelper.angleLerpSnap(this.angle, 0.0F);
            this.tint.a = 0.0F;
        }

        @Override
        protected void onClick() {
            toggle();
        }

        @Override
        public void update() {
            super.update();
        }

        @Override
        public void render(SpriteBatch sb) {
            Color alpha = Color.WHITE.cpy();

            if (!AiClient.active() && !canSendState()) {
                alpha = alpha.add(0, 0, 0, -.75f);
            }

            super.render(sb, alpha);
        }
    }

    private static boolean inCombat() {
        return CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null && AbstractDungeon
                .getCurrRoom() != null && AbstractDungeon
                .getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }

    public static void toggle() {
        if (AiClient.active()) AiClient.stop();
        else if (canSendState()) AiClient.start();
    }

    public static boolean canSendState() {
        return inCombat() && !AiClient.active() && readyForUpdate();
    }

    public static boolean readyForUpdate() {
        if (AbstractDungeon.actionManager == null || AbstractDungeon.player == null) {
            return false;
        }

        // Wait for exhaust effects to finish so they don't come back to haunt us.
        for (AbstractGameEffect effect : AbstractDungeon.effectList) {
            if (effect instanceof ExhaustCardEffect) {
                return false;
            }
        }

        // A selection screen is a decision point even after the single-action
        // controller has finished: the action that opened the screen consumed
        // the controller's only command, and the native worker still needs a
        // fresh state to emit HAND_SELECT/GRID_SELECT/CARD_REWARD_SELECT.  The
        // shouldStep() gate below would otherwise wait for a human click
        // (stalls on in-combat Discovery from Skill/Attack/Power/Potion-use).
        if (isSelectionScreenUp()) {
            return true;
        }

        if (AbstractDungeon.actionManager.turnHasEnded
                || (AbstractDungeon.actionManager.currentAction != null && AbstractDungeon.actionManager.phase == GameActionManager.Phase.EXECUTING_ACTIONS)
                || !AbstractDungeon.actionManager.isEmpty()) {
            return false;
        }

        return LudicrousSpeedMod.shouldStep();
    }

    private static boolean isSelectionScreenUp() {
        if (!AbstractDungeon.isScreenUp) {
            return false;
        }
        return AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT
                || AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID
                || AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD;
    }

    public enum ControllerMode {
        FULL_UI,
        TOP_PANEL_LAUNCHER,
        INVISIBLE
    }

    public static ControllerMode getModeOption() {
        SpireConfig config = BattleAiMod.optionsConfig;
        if (config == null || !config.has("controller_mode")) {
            return ControllerMode.FULL_UI;
        }

        return ControllerMode.valueOf(config.getString("controller_mode"));
    }

    public static void saveMode(ControllerMode mode) {
        SpireConfig config = BattleAiMod.optionsConfig;
        if (config != null) {
            config.setString("controller_mode", mode.toString());
            try {
                config.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean getAutoStartAi() {
        SpireConfig config = BattleAiMod.optionsConfig;
        if (config == null || !config.has("auto_start_ai")) {
            return false;
        }
        return config.getBool("auto_start_ai");
    }

    public static void saveAutoStartAi(boolean value) {
        SpireConfig config = BattleAiMod.optionsConfig;
        if (config != null) {
            config.setBool("auto_start_ai", value);
            try {
                config.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static SearchProfile getSearchProfile() {
        SpireConfig config = BattleAiMod.optionsConfig;
        if (config == null || !config.has("search_profile")) {
            return SearchProfile.BALANCED;
        }
        return SearchProfile.fromString(config.getString("search_profile"));
    }

    public static void saveSearchProfile(SearchProfile profile) {
        SpireConfig config = BattleAiMod.optionsConfig;
        if (config != null) {
            config.setString("search_profile", profile.toString());
            try {
                config.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isEnabled() {
        return BattleAiMod.battleClientControllerMode == ControllerMode.FULL_UI;
    }
}
