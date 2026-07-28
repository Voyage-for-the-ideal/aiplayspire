package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDiscardEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDrawPileEffect;

public class MakeTempCardInDiscardAndDeckAction extends AbstractGameAction {
    private AbstractCard cardToMake;

    public MakeTempCardInDiscardAndDeckAction(AbstractCard card) {
        UnlockTracker.markCardAsSeen(card.cardID);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.startDuration = Settings.FAST_MODE ? Settings.ACTION_DUR_FAST : 0.5F;
        this.duration = this.startDuration;
        this.cardToMake = card;
    }

    public void update() {
        if (this.duration == this.startDuration) {

            AbstractCard tmp = this.cardToMake.makeStatEquivalentCopy();
            AbstractDungeon.effectList.add(new ShowCardAndAddToDrawPileEffect(tmp,
                    Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH / 2.0F - 10.0F * Settings.xScale,
                    Settings.HEIGHT / 2.0F, true, false));

            tmp = this.cardToMake.makeStatEquivalentCopy();
            AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(tmp));
            tmp.current_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH / 2.0F + 10.0F * Settings.xScale;
            tmp.target_x = tmp.current_x;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * MakeTempCardInDiscardAndDeckAction.class Java compiler version: 8 (52.0)
 * JD-Core Version: 1.1.3
 */



