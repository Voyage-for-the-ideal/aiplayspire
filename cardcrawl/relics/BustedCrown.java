package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class BustedCrown extends AbstractRelic {
    private static final int CARDS_SUBTRACTED = 2;
    public static final String ID = "Busted Crown";

    public BustedCrown() {
        super("Busted Crown", "crown.png", RelicTier.BOSS, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[1] + this.DESCRIPTIONS[0];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onEquip() {
        AbstractDungeon.player.energy.energyMaster++;
    }

    public void onUnequip() {
        AbstractDungeon.player.energy.energyMaster--;
    }

    public int changeNumberOfCardsInReward(int numberOfCards) {
        return numberOfCards - 2;
    }

    public AbstractRelic makeCopy() {
        return new BustedCrown();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\BustedCrown
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

