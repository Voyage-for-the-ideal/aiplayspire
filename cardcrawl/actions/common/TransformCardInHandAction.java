package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class TransformCardInHandAction extends AbstractGameAction {
    private AbstractCard replacement;
    private int handIndex;

    public TransformCardInHandAction(int index, AbstractCard replacement) {
        this.handIndex = index;
        this.replacement = replacement;

        if (Settings.FAST_MODE) {
            this.startDuration = 0.05F;
        } else {
            this.startDuration = 0.15F;
        }
        this.duration = this.startDuration;
    }

    public void update() {
        if (this.duration == this.startDuration) {
            AbstractCard target = AbstractDungeon.player.hand.group.get(this.handIndex);
            this.replacement.current_x = target.current_x;
            this.replacement.current_y = target.current_y;
            this.replacement.target_x = target.target_x;
            this.replacement.target_y = target.target_y;
            this.replacement.drawScale = 1.0F;
            this.replacement.targetDrawScale = target.targetDrawScale;
            this.replacement.angle = target.angle;
            this.replacement.targetAngle = target.targetAngle;
            this.replacement.superFlash(Color.WHITE.cpy());
            AbstractDungeon.player.hand.group.set(this.handIndex, this.replacement);
            AbstractDungeon.player.hand.glowCheck();
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * TransformCardInHandAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */



