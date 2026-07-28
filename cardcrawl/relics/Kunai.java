package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.DexterityPower;

public class Kunai
        extends AbstractRelic {
    public static final String ID = "Kunai";

    public Kunai() {
        super("Kunai", "kunai.png", RelicTier.UNCOMMON, LandingSound.CLINK);
    }
    private static final int DEX_AMT = 1;
    private static final int NUM_CARDS = 3;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1] + '\001' + this.DESCRIPTIONS[2];
    }

    public void atTurnStart() {
        this.counter = 0;
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            this.counter++;

            if (this.counter % 3 == 0) {
                this.counter = 0;
                flash();
                addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                        (AbstractCreature) AbstractDungeon.player,
                        (AbstractPower) new DexterityPower((AbstractCreature) AbstractDungeon.player, 1), 1));
            }
        }
    }

    public void onVictory() {
        this.counter = -1;
    }

    public AbstractRelic makeCopy() {
        return new Kunai();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Kunai.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

