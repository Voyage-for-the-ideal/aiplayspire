package com.megacrit.cardcrawl.unlock.misc;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.unlock.AbstractUnlock;

public class TheSilentUnlock
        extends AbstractUnlock {
    public static final String KEY = "The Silent";

    public void onUnlockScreenOpen() {
        this.player = CardCrawlGame.characterManager.getCharacter(AbstractPlayer.PlayerClass.THE_SILENT);
        this.player.drawX = Settings.WIDTH / 2.0F - 20.0F * Settings.scale;
        this.player.drawY = Settings.HEIGHT / 2.0F - 118.0F * Settings.scale;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\unlock\misc\
 * TheSilentUnlock.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
