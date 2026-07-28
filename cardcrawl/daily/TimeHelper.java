package com.megacrit.cardcrawl.daily;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeHelper {
    private static final Logger logger = LogManager.getLogger(TimeHelper.class.getName());
    public static boolean isTimeSet = false;
    private static long daySince1970;
    private static long timeServerTime;
    private static long endTimeMs;
    private static final float UPDATE_FREQ = -1.0F;
    private static float updateTimer = -1.0F;
    private static boolean offlineMode = false;
    private static DateFormat format = new SimpleDateFormat(
            (CardCrawlGame.languagePack.getUIString("DailyScreen")).TEXT[17]);

    public static void setTime(long unixTime, boolean isOfflineMode) {
        if (!isTimeSet) {
            offlineMode = isOfflineMode;
            timeServerTime = unixTime;
            daySince1970 = timeServerTime / 86400L;
            logger.info("Setting time to: " + timeServerTime);
            endTimeMs = (daySince1970 + 1L) * 86400L * 1000L;
            logger.info("Day was set!");
            isTimeSet = true;
            format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        }
    }

    public static boolean isOfflineMode() {
        return offlineMode;
    }

    public static long daySince1970() {
        return daySince1970;
    }

    public static void update() {
        if (isTimeSet) {
            updateTimer -= Gdx.graphics.getDeltaTime();
            if (updateTimer < 0.0F) {
                updateTimer = -1.0F;
            }
        }
    }

    public static String getTodayDate() {
        return format.format(new Date(System.currentTimeMillis()));
    }

    public static String getDate(long numDaysSince1970) {
        long unixTimestampMs = (daySince1970 - numDaysSince1970) * 86400L * 1000L;
        return format.format(new Date(System.currentTimeMillis() - unixTimestampMs));
    }

    public static String getTimeLeft() {
        if (endTimeMs - System.currentTimeMillis() < 0L) {
            endTimeMs += 86400000L;
            daySince1970++;
        }

        long remainingSec = (endTimeMs - System.currentTimeMillis()) / 1000L;
        long hours = remainingSec / 3600L;
        remainingSec %= 3600L;
        long minutes = remainingSec / 60L;
        return String.format("%02d:%02d:%02d",
                new Object[] {Long.valueOf(hours), Long.valueOf(minutes), Long.valueOf(remainingSec % 60L) });
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\TimeHelper.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

