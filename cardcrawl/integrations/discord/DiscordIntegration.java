package com.megacrit.cardcrawl.integrations.discord;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.integrations.DistributorFactory;
import com.megacrit.cardcrawl.integrations.PublisherIntegration;
import com.megacrit.cardcrawl.screens.leaderboards.FilterButton;
import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class DiscordIntegration
        implements PublisherIntegration {
    private static final Logger logger = LogManager.getLogger(DiscordIntegration.class.getName());
    private String[] TEXT;

    public DiscordIntegration() {
        String appId = "406644123832156160";

        File unpackPath = new File("discord-rpc");
        unpackPath.deleteOnExit();
        if (!unpackPath.canWrite()) {
            logger.warn("cannot write to dll unpack path: " + unpackPath.getAbsolutePath());
        }
        if (!unpackPath.exists() &&
                !unpackPath.mkdir()) {
            logger.warn("Failed to create the directory for " + unpackPath.getAbsolutePath());
        }

        logger.info("Unpacking discord rpc dll to " + unpackPath.getAbsolutePath());
        DiscordRPC.discordInitialize(appId, new DiscordEventHandlers(), true, null, unpackPath);
    }

    public boolean isInitialized() {
        return false;
    }

    public void dispose() {
    }

    public void deleteAllCloudFiles() {
    }

    public boolean setStat(String id, int value) {
        return false;
    }

    public int getStat(String id) {
        return 0;
    }

    public boolean incrementStat(String id, int incrementAmt) {
        return false;
    }

    public long getGlobalStat(String id) {
        return 0L;
    }

    public void uploadDailyLeaderboardScore(String name, int score) {
    }

    public void uploadLeaderboardScore(String name, int score) {
    }

    public void unlockAchievement(String id) {
    }

    public void getLeaderboardEntries(AbstractPlayer.PlayerClass pClass, FilterButton.RegionSetting rSetting,
            FilterButton.LeaderboardType lType, int start, int end) {
    }

    public void getDailyLeaderboard(long date, int start, int end) {
    }

    public void setRichPresenceDisplayPlaying(int floor, String character) {
        if (this.TEXT == null) {
            this.TEXT = (CardCrawlGame.languagePack.getUIString("RichPresence")).TEXT;
        }
        if (Settings.isDailyRun) {
            String msg = String.format(this.TEXT[0], new Object[] {Integer.valueOf(floor) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        } else if (Settings.isTrial) {
            String msg = String.format(this.TEXT[1], new Object[] {Integer.valueOf(floor) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        } else {
            String msg = String.format(character + this.TEXT[2], new Object[] {Integer.valueOf(floor) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        }
    }

    public void setRichPresenceDisplayPlaying(int floor, int ascension, String character) {
        if (this.TEXT == null) {
            this.TEXT = (CardCrawlGame.languagePack.getUIString("RichPresence")).TEXT;
        }
        if (Settings.isDailyRun) {
            String msg = String.format(this.TEXT[0], new Object[] {Integer.valueOf(floor) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        } else if (Settings.isTrial) {
            String msg = String.format(this.TEXT[1], new Object[] {Integer.valueOf(floor) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        } else if (Settings.language == Settings.GameLanguage.ENG || Settings.language == Settings.GameLanguage.DEU
                || Settings.language == Settings.GameLanguage.TUR || Settings.language == Settings.GameLanguage.KOR
                || Settings.language == Settings.GameLanguage.RUS || Settings.language == Settings.GameLanguage.SPA
                || Settings.language == Settings.GameLanguage.DUT) {

            String msg = String.format(this.TEXT[4] + character + this.TEXT[2],
                    new Object[] {Integer.valueOf(ascension), Integer.valueOf(floor) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        } else {
            String msg = String.format(character + this.TEXT[2] + this.TEXT[4],
                    new Object[] {Integer.valueOf(floor), Integer.valueOf(ascension) });
            logger.debug("Setting Rich Presence: " + msg);
            setRichPresenceData("", msg);
        }
    }

    public void setRichPresenceDisplayInMenu() {
        if (this.TEXT == null) {
            this.TEXT = (CardCrawlGame.languagePack.getUIString("RichPresence")).TEXT;
        }
        logger.debug("Setting Rich Presence: " + this.TEXT[3]);
        setRichPresenceData(this.TEXT[3], "");
    }

    public int getNumUnlockedAchievements() {
        return 0;
    }

    public DistributorFactory.Distributor getType() {
        return DistributorFactory.Distributor.DISCORD;
    }

    private void setRichPresenceData(String state, String details) {
        DiscordRichPresence rich = (new DiscordRichPresence.Builder(state)).setDetails(details).build();
        DiscordRPC.discordUpdatePresence(rich);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\integrations\
 * discord\DiscordIntegration.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

