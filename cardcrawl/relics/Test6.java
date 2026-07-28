package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Test6
        extends AbstractRelic {
    public static final String ID = "Test 6";

    public Test6() {
        super("Test 6", "test6.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }
    private static final int GOLD_REQ = 100;
    private static final int BLOCK_AMT = 3;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1] + 'd' + this.DESCRIPTIONS[2];
    }

    public void onPlayerEndTurn() {
        if (hasEnoughGold()) {
            flash();
            this.pulse = false;
            addToTop((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player, 3 * AbstractDungeon.player.gold / 100));

            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public void atTurnStart() {
        if (hasEnoughGold()) {
            this.pulse = true;
            beginPulse();
        }
    }

    public void onVictory() {
        this.pulse = false;
    }

    private boolean hasEnoughGold() {
        return (AbstractDungeon.player.gold >= 100);
    }

    public AbstractRelic makeCopy() {
        return new Test6();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Test6.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

