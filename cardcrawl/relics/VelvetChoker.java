package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class VelvetChoker
        extends AbstractRelic {
    public static final String ID = "Velvet Choker";
    private static final int PLAY_LIMIT = 6;

    public VelvetChoker() {
        super("Velvet Choker", "redChoker.png", RelicTier.BOSS, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[2] + this.DESCRIPTIONS[0] + '\006' + this.DESCRIPTIONS[1];
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

    public void atBattleStart() {
        this.counter = 0;
    }

    public void atTurnStart() {
        this.counter = 0;
    }

    public void onPlayCard(AbstractCard card, AbstractMonster m) {
        if (this.counter < 6) {
            this.counter++;
            if (this.counter >= 6) {
                flash();
            }
        }
    }

    public boolean canPlay(AbstractCard card) {
        if (this.counter >= 6) {
            card.cantUseMessage = this.DESCRIPTIONS[3] + '\006' + this.DESCRIPTIONS[1];
            return false;
        }
        return true;
    }

    public void onVictory() {
        this.counter = -1;
    }

    public AbstractRelic makeCopy() {
        return new VelvetChoker();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * VelvetChoker.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

