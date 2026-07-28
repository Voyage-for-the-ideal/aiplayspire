package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MalleablePower
        extends AbstractPower {
    public static final String POWER_ID = "Malleable";

    public MalleablePower(AbstractCreature owner) {
        this(owner, 3);
    }

    public MalleablePower(AbstractCreature owner, int amt) {
        this.name = NAME;
        this.ID = "Malleable";
        this.owner = owner;
        this.amount = amt;
        this.basePower = amt;
        updateDescription();
        loadRegion("malleable");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1] + NAME + DESCRIPTIONS[2] + this.basePower
                + DESCRIPTIONS[3];
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (this.owner.isPlayer)
            return;
        this.amount = this.basePower;
        updateDescription();
    }

    public void atEndOfRound() {
        if (!this.owner.isPlayer)
            return;
        this.amount = this.basePower;
        updateDescription();
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (damageAmount < this.owner.currentHealth && damageAmount > 0 && info.owner != null
                && info.type == DamageInfo.DamageType.NORMAL && info.type != DamageInfo.DamageType.HP_LOSS) {

            flash();
            if (this.owner.isPlayer) {
                addToTop((AbstractGameAction) new GainBlockAction(this.owner, this.owner, this.amount));
            } else {
                addToBot((AbstractGameAction) new GainBlockAction(this.owner, this.owner, this.amount));
            }
            this.amount++;
            updateDescription();
        }
        return damageAmount;
    }

    public void stackPower(int stackAmount) {
        this.amount += stackAmount;
        this.basePower += stackAmount;
    }

    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Malleable");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private static final int STARTING_BLOCK = 3;
    private int basePower;
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * MalleablePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

