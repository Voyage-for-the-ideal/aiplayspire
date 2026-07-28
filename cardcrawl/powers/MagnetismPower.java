package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MagnetismPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Magnetism");
    public static final String POWER_ID = "Magnetism";
    public static final String NAME = powerStrings.NAME;
    public static final String SINGULAR_DESCRIPTION = powerStrings.DESCRIPTIONS[0];
    public static final String PLURAL_DESCRIPTION = powerStrings.DESCRIPTIONS[1];

    public MagnetismPower(AbstractCreature owner, int cardAmount) {
        this.name = NAME;
        this.ID = "Magnetism";
        this.owner = owner;
        this.amount = cardAmount;
        updateDescription();
        loadRegion("magnet");
    }

    public void atStartOfTurn() {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flash();
            for (int i = 0; i < this.amount; i++) {
                addToBot((AbstractGameAction) new MakeTempCardInHandAction(

                        AbstractDungeon.returnTrulyRandomColorlessCardInCombat().makeCopy(), 1, false));
            }
        }
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
    }

    public void updateDescription() {
        if (this.amount > 1) {
            this.description = String.format(PLURAL_DESCRIPTION, new Object[] {Integer.valueOf(this.amount) });
        } else {
            this.description = String.format(SINGULAR_DESCRIPTION, new Object[] {Integer.valueOf(this.amount) });
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * MagnetismPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

