package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.localization.LocalizedStrings;

public class DarkstonePeriapt
        extends AbstractRelic {
    public static final String ID = "Darkstone Periapt";
    private static final int HP_AMT = 6;

    public DarkstonePeriapt() {
        super("Darkstone Periapt", "darkstone.png", RelicTier.UNCOMMON, LandingSound.MAGICAL);
    }

    public void onObtainCard(AbstractCard card) {
        if (card.color == AbstractCard.CardColor.CURSE) {
            if (ModHelper.isModEnabled("Hoarder")) {
                AbstractDungeon.player.increaseMaxHp(6, true);
                AbstractDungeon.player.increaseMaxHp(6, true);
            }
            AbstractDungeon.player.increaseMaxHp(6, true);
        }
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\006' + LocalizedStrings.PERIOD;
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new DarkstonePeriapt();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * DarkstonePeriapt.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

