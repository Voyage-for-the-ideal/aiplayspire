package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Nunchaku extends AbstractRelic {
    public static final String ID = "Nunchaku";

    public Nunchaku() {
        super("Nunchaku", "nunchaku.png", RelicTier.COMMON, LandingSound.FLAT);
        this.counter = 0;
    }
    private static final int NUM_CARDS = 10;

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0] + '\n' + this.DESCRIPTIONS[1];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            this.counter++;

            if (this.counter % 10 == 0) {
                this.counter = 0;
                flash();
                addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                addToBot((AbstractGameAction) new GainEnergyAction(1));
            }
        }
    }

    public AbstractRelic makeCopy() {
        return new Nunchaku();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Nunchaku.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

