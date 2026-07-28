package com.megacrit.cardcrawl.integrations.steam;

import com.codedisaster.steamworks.SteamAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SteamTicker
        implements Runnable {
    private static final Logger logger = LogManager.getLogger(SteamTicker.class.getName());

    public void run() {
        logger.info("Steam Ticker initialized.");
        while (SteamAPI.isSteamRunning()) {
            try {
                Thread.sleep(66L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            SteamAPI.runCallbacks();
        }

        logger.info("[ERROR] SteamAPI stopped running.");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\integrations\steam
 * \SteamTicker.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

