package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.SuicideAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.ExplosionSmallEffect;

public class FadingPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Fading");
    public static final String POWER_ID = "Fading";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public FadingPower(AbstractCreature owner, int turns) {
        this.name = NAME;
        this.ID = "Fading";
        this.owner = owner;
        this.amount = turns;
        updateDescription();
        loadRegion("fading");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[2];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        }
    }

    public void duringTurn() {
        if (this.amount == 1 && !this.owner.isDying) {
            addToBot((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new ExplosionSmallEffect(this.owner.hb.cX, this.owner.hb.cY), 0.1F));
            addToBot((AbstractGameAction) new SuicideAction((AbstractMonster) this.owner));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Fading", 1));
            updateDescription();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\FadingPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

