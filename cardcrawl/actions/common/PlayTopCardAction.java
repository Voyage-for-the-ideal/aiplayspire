package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.NewQueueCardAction;
import com.megacrit.cardcrawl.actions.utility.UnlimboAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PlayTopCardAction extends AbstractGameAction {
    private boolean exhaustCards;

    public PlayTopCardAction(AbstractCreature target, boolean exhausts) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.actionType = ActionType.WAIT;
        this.source = (AbstractCreature) AbstractDungeon.player;
        this.target = target;
        this.exhaustCards = exhausts;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (AbstractDungeon.player.drawPile.size() + AbstractDungeon.player.discardPile.size() == 0) {
                this.isDone = true;

                return;
            }
            if (AbstractDungeon.player.drawPile.isEmpty()) {
                addToTop(new PlayTopCardAction(this.target, this.exhaustCards));
                addToTop(new EmptyDeckShuffleAction());
                this.isDone = true;

                return;
            }
            if (!AbstractDungeon.player.drawPile.isEmpty()) {
                AbstractCard card = AbstractDungeon.player.drawPile.getTopCard();
                AbstractDungeon.player.drawPile.group.remove(card);
                (AbstractDungeon.getCurrRoom()).souls.remove(card);
                card.exhaustOnUseOnce = this.exhaustCards;
                AbstractDungeon.player.limbo.group.add(card);
                card.current_y = -200.0F * Settings.scale;
                card.target_x = Settings.WIDTH / 2.0F + 200.0F * Settings.xScale;
                card.target_y = Settings.HEIGHT / 2.0F;
                card.targetAngle = 0.0F;
                card.lighten(false);
                card.drawScale = 0.12F;
                card.targetDrawScale = 0.75F;

                card.applyPowers();
                addToTop((AbstractGameAction) new NewQueueCardAction(card, this.target, false, true));
                addToTop((AbstractGameAction) new UnlimboAction(card));
                if (!Settings.FAST_MODE) {
                    addToTop((AbstractGameAction) new WaitAction(Settings.ACTION_DUR_MED));
                } else {
                    addToTop((AbstractGameAction) new WaitAction(Settings.ACTION_DUR_FASTER));
                }
            }
            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * PlayTopCardAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



