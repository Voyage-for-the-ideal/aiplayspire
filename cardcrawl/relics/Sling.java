package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class Sling extends AbstractRelic {
    public static final String ID = "Sling";

    public Sling() {
        super("Sling", "sling.png", RelicTier.SHOP, LandingSound.CLINK);
    }
    private static final int STR = 2;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        if ((AbstractDungeon.getCurrRoom()).eliteTrigger) {
            flash();
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, 2), 2));

            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public AbstractRelic makeCopy() {
        return new Sling();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Sling.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

