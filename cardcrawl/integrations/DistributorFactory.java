package com.megacrit.cardcrawl.integrations;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.integrations.discord.DiscordIntegration;
import com.megacrit.cardcrawl.integrations.ea.EaIntegration;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import com.megacrit.cardcrawl.integrations.microsoft.MicrosoftIntegration;
import com.megacrit.cardcrawl.integrations.steam.SteamIntegration;
import com.megacrit.cardcrawl.integrations.wegame.WeGameIntegration;

public class DistributorFactory {
    public enum Distributor {
        STEAM, DISCORD, WEGAME, GOG, EA, MICROSOFT;
    }

    public static PublisherIntegration getEnabledDistributor(String distributor) throws DistributorFactoryException {
        switch (distributor) {
            case "steam":
                return (PublisherIntegration) new SteamIntegration();
            case "discord":
                return (PublisherIntegration) new DiscordIntegration();
            case "wegame":
                return (PublisherIntegration) new WeGameIntegration();
            case "gog":
                return (PublisherIntegration) new GogIntegration();
            case "ea":
                return (PublisherIntegration) new EaIntegration();
            case "microsoft":
                return (PublisherIntegration) new MicrosoftIntegration();
        }
        throw new DistributorFactoryException("Unrecognized distributor=" + distributor);
    }

    public static boolean isLeaderboardEnabled() {
        return (CardCrawlGame.publisherIntegration.getType() == Distributor.STEAM);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\integrations\
 * DistributorFactory.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

