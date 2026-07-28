package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ArtifactPower;

public class ClockworkSouvenir extends AbstractRelic {
    public ClockworkSouvenir() {
        super("ClockworkSouvenir", "clockwork.png", RelicTier.SHOP, LandingSound.HEAVY);
    }
    public static final String ID = "ClockworkSouvenir";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atBattleStart() {
        flash();
        addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player,
                (AbstractPower) new ArtifactPower((AbstractCreature) AbstractDungeon.player, 1), 1));
    }

    public AbstractRelic makeCopy() {
        return new ClockworkSouvenir();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * ClockworkSouvenir.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

