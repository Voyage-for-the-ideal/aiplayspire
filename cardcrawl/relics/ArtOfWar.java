package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class ArtOfWar extends AbstractRelic {
    public static final String ID = "Art of War";
    private boolean gainEnergyNext = false, firstTurn = false;

    public ArtOfWar() {
        super("Art of War", "artOfWar.png", RelicTier.COMMON, LandingSound.FLAT);
        this.pulse = false;
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0] + this.DESCRIPTIONS[1];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void atPreBattle() {
        flash();
        this.firstTurn = true;
        this.gainEnergyNext = true;
        if (!this.pulse) {
            beginPulse();
            this.pulse = true;
        }
    }

    public void atTurnStart() {
        beginPulse();
        this.pulse = true;
        if (this.gainEnergyNext && !this.firstTurn) {
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new GainEnergyAction(1));
        }
        this.firstTurn = false;
        this.gainEnergyNext = true;
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            this.gainEnergyNext = false;
            this.pulse = false;
        }
    }

    public void onVictory() {
        this.pulse = false;
    }

    public AbstractRelic makeCopy() {
        return new ArtOfWar();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\ArtOfWar.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

