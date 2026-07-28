package com.megacrit.cardcrawl.helpers.steamInput;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamController;
import com.codedisaster.steamworks.SteamControllerActionSetHandle;
import com.codedisaster.steamworks.SteamControllerHandle;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import net.java.games.input.Controller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class SteamInputHelper {
    private static final Logger logger = LogManager.getLogger(SteamInputHelper.class.getName());
    public static Array<Controller> controllers = null;
    public static ArrayList<SteamInputAction> actions = new ArrayList<>();
    public static CInputHelper.ControllerModel model = null;

    public static boolean alive = false;

    public static SteamController controller;
    public static SteamControllerHandle[] controllerHandles;
    public static SteamControllerHandle handle;
    public static int numControllers = 0;

    public static SteamControllerActionSetHandle setHandle;

    public SteamInputHelper() {
        logger.error("Initializing Steam controller...");

        if (!SteamAPI.isSteamRunning()) {
            logger.info("Steam isn't running? SteamInput is disabled.");

            return;
        }

        controller = new SteamController();
        alive = controller.init();

        if (!alive) {
            logger.info("Steam controller failed to initialize.");

            return;
        }
        controllerHandles = new SteamControllerHandle[16];

        logger.info("Starting input detection...");
        Thread controllerDetectThread = new Thread(new SteamInputDetect());
        CardCrawlGame.sInputDetectThread = controllerDetectThread;
        controllerDetectThread.setName("InputDetect");
        controllerDetectThread.start();

        model = CInputHelper.ControllerModel.XBOX_ONE;
        ImageMaster.loadControllerImages(CInputHelper.ControllerModel.XBOX_ONE);
    }

    public static void initActions(SteamControllerHandle controllerHandle) {
        handle = controllerHandle;
        SteamInputActionSet.load(controller);

        for (SteamInputAction a : actions) {
            a.init(controller, handle);
        }
    }

    public static void updateFirst() {
        controller.runFrame();

        for (SteamInputAction a : actions) {
            a.update();
        }
    }

    public static void setCursor(Hitbox hb) {
        if (hb != null) {
            Gdx.input.setCursorPosition((int) hb.cX, Settings.HEIGHT - (int) hb.cY);
        }
    }

    public static boolean isJustPressed(int keycode) {
        return false;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\steamInput
 * \SteamInputHelper.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

