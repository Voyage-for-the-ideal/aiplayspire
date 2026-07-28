package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class TranscendenceAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_MED) {
            AbstractPlayer p = AbstractDungeon.player;

            for (AbstractCard c : p.hand.group) {
                if (c.canUpgrade() && (c.retain || c.selfRetain)) {
                    c.superFlash();
                    c.upgrade();
                    c.applyPowers();
                }
            }

            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * TranscendenceAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



