package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.ChooseOneColorless;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Toolbox extends AbstractRelic {
    public Toolbox() {
        super("Toolbox", "toolbox.png", RelicTier.SHOP, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }
    public static final String ID = "Toolbox";

    public void atBattleStartPreDraw() {
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToBot((AbstractGameAction) new ChooseOneColorless());
    }

    public AbstractRelic makeCopy() {
        return new Toolbox();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Toolbox.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

