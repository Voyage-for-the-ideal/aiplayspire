package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class DuVuDoll extends AbstractRelic {
    public static final String ID = "Du-Vu Doll";

    public DuVuDoll() {
        super("Du-Vu Doll", "duvuDoll.png", RelicTier.RARE, LandingSound.MAGICAL);
    }
    private static final int AMT = 1;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public void setCounter(int c) {
        this.counter = c;
        if (this.counter == 0) {
            this.description = this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[2];
        } else {
            this.description = this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[3]
                    + this.counter + this.DESCRIPTIONS[4];
        }

        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onMasterDeckChange() {
        this.counter = 0;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.type == AbstractCard.CardType.CURSE) {
                this.counter++;
            }
        }

        if (this.counter == 0) {
            this.description = this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[2];
        } else {
            this.description = this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[3]
                    + this.counter + this.DESCRIPTIONS[4];
        }

        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onEquip() {
        this.counter = 0;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.type == AbstractCard.CardType.CURSE) {
                this.counter++;
            }
        }

        if (this.counter == 0) {
            this.description = this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[2];
        } else {
            this.description = this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[3]
                    + this.counter + this.DESCRIPTIONS[4];
        }

        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void atBattleStart() {
        if (this.counter > 0) {
            flash();
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, this.counter),
                    this.counter));

            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public AbstractRelic makeCopy() {
        return new DuVuDoll();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\DuVuDoll.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

