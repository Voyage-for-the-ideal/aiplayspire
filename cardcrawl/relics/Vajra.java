package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class Vajra extends AbstractRelic {
    public static final String ID = "Vajra";

    public Vajra() {
        super("Vajra", "vajra.png", RelicTier.COMMON, LandingSound.CLINK);
    }
    private static final int STR = 1;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        flash();
        addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player,
                (AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, 1), 1));

        addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
    }

    public AbstractRelic makeCopy() {
        return new Vajra();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Vajra.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

