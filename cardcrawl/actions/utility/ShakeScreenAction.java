package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.ScreenShake;

public class ShakeScreenAction
        extends AbstractGameAction {
    private float startDur;
    ScreenShake.ShakeDur shakeDur;
    ScreenShake.ShakeIntensity intensity;

    public ShakeScreenAction(float duration, ScreenShake.ShakeDur dur, ScreenShake.ShakeIntensity intensity) {
        this.duration = duration;
        this.startDur = duration;
        this.shakeDur = dur;
        this.intensity = intensity;
        this.actionType = ActionType.WAIT;
    }

    public void update() {
        if (this.duration == this.startDur) {
            CardCrawlGame.screenShake.shake(this.intensity, this.shakeDur, false);
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * ShakeScreenAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



