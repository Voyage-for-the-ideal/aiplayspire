package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PenNibPower;

public class PenNib extends AbstractRelic {
    public static final String ID = "Pen Nib";

    public PenNib() {
        super("Pen Nib", "penNib.png", RelicTier.COMMON, LandingSound.CLINK);
        this.counter = 0;
    }
    public static final int COUNT = 10;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            this.counter++;

            if (this.counter == 10) {
                this.counter = 0;
                flash();
                this.pulse = false;
            } else if (this.counter == 9) {
                beginPulse();
                this.pulse = true;
                AbstractDungeon.player.hand.refreshHandLayout();
                addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                        (AbstractCreature) AbstractDungeon.player,
                        (AbstractPower) new PenNibPower((AbstractCreature) AbstractDungeon.player, 1), 1, true));
            }
        }
    }

    public void atBattleStart() {
        if (this.counter == 9) {
            beginPulse();
            this.pulse = true;
            AbstractDungeon.player.hand.refreshHandLayout();
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new PenNibPower((AbstractCreature) AbstractDungeon.player, 1), 1, true));
        }
    }

    public AbstractRelic makeCopy() {
        return new PenNib();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\PenNib.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

