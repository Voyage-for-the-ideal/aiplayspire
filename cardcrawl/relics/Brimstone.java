package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class Brimstone extends AbstractRelic {
    public static final String ID = "Brimstone";

    public Brimstone() {
        super("Brimstone", "brimstone.png", RelicTier.SHOP, LandingSound.CLINK);
    }
    private static final int STR_AMT = 2;
    private static final int ENEMY_STR_AMT = 1;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[1] + '\001' + this.DESCRIPTIONS[2];
    }

    public void atTurnStart() {
        flash();
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player,
                (AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, 2), 2));

        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) m,
                    (AbstractPower) new StrengthPower((AbstractCreature) m, 1), 1));
        }
    }

    public AbstractRelic makeCopy() {
        return new Brimstone();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Brimstone.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

