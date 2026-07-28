package com.megacrit.cardcrawl.relics;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Orichalcum extends AbstractRelic {
    public static final String ID = "Orichalcum";

    public Orichalcum() {
        super("Orichalcum", "orichalcum.png", RelicTier.COMMON, LandingSound.HEAVY);
    }
    private static final int BLOCK_AMT = 6;
    public boolean trigger = false;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\006' + this.DESCRIPTIONS[1];
    }

    public void onPlayerEndTurn() {
        if (AbstractDungeon.player.currentBlock == 0 || this.trigger) {
            this.trigger = false;
            flash();
            stopPulse();
            addToTop((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player, 6));
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public void atTurnStart() {
        this.trigger = false;
        if (AbstractDungeon.player.currentBlock == 0) {
            beginLongPulse();
        }
    }

    public int onPlayerGainedBlock(float blockAmount) {
        if (blockAmount > 0.0F) {
            stopPulse();
        }

        return MathUtils.floor(blockAmount);
    }

    public void onVictory() {
        stopPulse();
    }

    public AbstractRelic makeCopy() {
        return new Orichalcum();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Orichalcum.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

