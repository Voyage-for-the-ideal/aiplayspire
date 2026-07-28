package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class SpiritShieldAction
        extends AbstractGameAction {
    public SpiritShieldAction(int blockPerCard) {
        this.blockPerCard = blockPerCard;
    }
    private int blockPerCard;

    public void update() {
        if (!AbstractDungeon.player.hand.isEmpty()) {
            addToTop((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player, AbstractDungeon.player.hand.group

                            .size() * this.blockPerCard));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * SpiritShieldAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



