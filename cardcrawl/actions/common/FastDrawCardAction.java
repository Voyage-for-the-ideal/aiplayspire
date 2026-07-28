package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.SoulGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.PlayerTurnEffect;

public class FastDrawCardAction
        extends AbstractGameAction {
    private boolean shuffleCheck = false;

    public FastDrawCardAction(AbstractCreature source, int amount, boolean endTurnDraw) {
        if (endTurnDraw) {
            AbstractDungeon.effectList.add(new PlayerTurnEffect());
        } else if (AbstractDungeon.player.hasPower("No Draw")) {
            AbstractDungeon.player.getPower("No Draw").flash();
            setValues((AbstractCreature) AbstractDungeon.player, source, amount);
            this.isDone = true;
            this.duration = 0.0F;
            this.actionType = ActionType.WAIT;

            return;
        }
        setValues((AbstractCreature) AbstractDungeon.player, source, amount);
        this.actionType = ActionType.DRAW;
        this.duration = Settings.ACTION_DUR_XFAST;
    }

    public FastDrawCardAction(AbstractCreature source, int amount) {
        this(source, amount, false);
    }

    public void update() {
        int deckSize = AbstractDungeon.player.drawPile.size();
        int discardSize = AbstractDungeon.player.discardPile.size();

        if (SoulGroup.isActive()) {
            return;
        }

        if (deckSize + discardSize == 0) {
            this.isDone = true;

            return;
        }

        if (!this.shuffleCheck) {
            if (this.amount + AbstractDungeon.player.hand.size() > 10) {
                int handSizeAndDraw = 10 - this.amount + AbstractDungeon.player.hand.size();
                this.amount += handSizeAndDraw;
                AbstractDungeon.player.createHandIsFullDialog();
            }
            if (this.amount > deckSize) {
                int tmp = this.amount - deckSize;
                addToTop(new FastDrawCardAction((AbstractCreature) AbstractDungeon.player, tmp));
                addToTop(new EmptyDeckShuffleAction());
                if (deckSize != 0) {
                    addToTop(new FastDrawCardAction((AbstractCreature) AbstractDungeon.player, deckSize));
                }
                this.amount = 0;
                this.isDone = true;
            }
            this.shuffleCheck = true;
        }

        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.amount != 0 && this.duration < 0.0F) {
            this.duration = Settings.ACTION_DUR_XFAST;
            this.amount--;
            AbstractDungeon.player.draw();
            AbstractDungeon.player.hand.refreshHandLayout();

            if (this.amount == 0)
                this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * FastDrawCardAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



