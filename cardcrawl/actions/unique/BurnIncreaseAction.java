package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDiscardEffect;

public class BurnIncreaseAction extends AbstractGameAction {
    private static final float DURATION = 3.0F;
    private boolean gotBurned = false;

    public BurnIncreaseAction() {
        this.duration = 3.0F;
        this.actionType = ActionType.WAIT;
    }

    public void update() {
        if (this.duration == 3.0F) {
            for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
                if (c instanceof Burn) {
                    c.upgrade();
                }
            }

            for (AbstractCard c : AbstractDungeon.player.drawPile.group) {
                if (c instanceof Burn) {
                    c.upgrade();
                }
            }
        }

        if (this.duration < 1.5F && !this.gotBurned) {
            this.gotBurned = true;
            Burn b = new Burn();
            b.upgrade();
            AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect((AbstractCard) b));
            Burn c = new Burn();
            c.upgrade();
            AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect((AbstractCard) c));
            Burn d = new Burn();
            d.upgrade();
            AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect((AbstractCard) d));
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * BurnIncreaseAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



