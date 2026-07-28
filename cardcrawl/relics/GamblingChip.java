package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.unique.GamblingChipAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class GamblingChip extends AbstractRelic {
    public static final String ID = "Gambling Chip";

    public GamblingChip() {
        super("Gambling Chip", "gamblingChip.png", RelicTier.RARE, LandingSound.FLAT);
    }
    private boolean activated = false;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atBattleStartPreDraw() {
        this.activated = false;
    }

    public void atTurnStartPostDraw() {
        if (!this.activated) {
            this.activated = true;
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new GamblingChipAction((AbstractCreature) AbstractDungeon.player));
        }
    }

    public AbstractRelic makeCopy() {
        return new GamblingChip();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * GamblingChip.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

