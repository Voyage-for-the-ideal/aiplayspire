package com.megacrit.cardcrawl.helpers.steamInput;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SteamInputDetect
        implements Runnable {
    private static final Logger logger = LogManager.getLogger(SteamInputDetect.class.getName());

    public void run() {
        int tries = 0;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                tries++;

                int num = 0;
                num = SteamInputHelper.controller.getConnectedControllers(SteamInputHelper.controllerHandles);

                if (num != 0) {
                    SteamInputHelper.initActions(SteamInputHelper.controllerHandles[0]);
                    Settings.isControllerMode = true;
                    Settings.isTouchScreen = false;

                    logger.info("Steam Input controller found!");

                    SteamInputHelper.numControllers = 1;
                    Thread.currentThread().interrupt();
                } else if (tries == 12) {
                    SteamInputHelper.numControllers = 999;
                    Thread.currentThread().interrupt();
                }

                Thread.sleep(500L);
            } catch (InterruptedException e) {
                logger.info("Steam input detect thread interrupted!");
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Steam input detect thread will die now.");
        CardCrawlGame.sInputDetectThread = null;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\steamInput
 * \SteamInputDetect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

