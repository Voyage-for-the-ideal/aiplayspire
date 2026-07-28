package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class MakeTempCardAtBottomOfDeckAction
        extends AbstractGameAction {
    public MakeTempCardAtBottomOfDeckAction(int amount) {
        setValues(this.target, this.source, amount);
        this.actionType = ActionType.CARD_MANIPULATION;

        this.startDuration = Settings.FAST_MODE ? Settings.ACTION_DUR_FAST : 0.5F;
        this.duration = this.startDuration;
    }

    public void update() {
        if (this.duration == this.startDuration) {
            for (int i = 0; i < this.amount; i++) {
                AbstractCard c = AbstractDungeon.returnTrulyRandomCardInCombat().makeStatEquivalentCopy();
                UnlockTracker.markCardAsSeen(c.cardID);
                if (c.type != AbstractCard.CardType.CURSE && c.type != AbstractCard.CardType.STATUS
                        && AbstractDungeon.player.hasPower("MasterRealityPower")) {
                    c.upgrade();
                }

                AbstractDungeon.player.drawPile.addToBottom(c);
            }

            this.duration -= Gdx.graphics.getDeltaTime();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * MakeTempCardAtBottomOfDeckAction.class Java compiler version: 8 (52.0)
 * JD-Core Version: 1.1.3
 */



